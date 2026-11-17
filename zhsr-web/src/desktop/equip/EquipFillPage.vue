<template>
  <DesktopFrame :back="back">
    <template #header>
      <h1 class="desktop-frame__title">
        {{ editId !== null ? t('Изменение описи') : t('Новая опись') }}
      </h1>
    </template>

    <div class="equip-fill q-pa-md">
      <ErrorBox v-if="fill.loadError.value" :error="fill.loadError.value" :retry="retry" class="q-mb-md" />

      <!-- исправление: шаблон и вагон — из описи, не меняются -->
      <div v-if="editId !== null" class="equip-fill__fixed">
        <span>{{ fill.template.value ? textOf(fill.template.value) : '' }}</span>
        <span v-if="fill.wagon.value" class="zs-mono">{{ fill.wagon.value.number }}</span>
      </div>
      <div v-else class="equip-fill__subject">
        <FormField label="Шаблон описи">
          <CbList v-model="templateChoice" single placeholder="Выберите шаблон описи" :values="templateChoices" />
        </FormField>
        <FormField label="Вагон">
          <RequestWagonField v-model="wagonId" @pick="onWagon" />
        </FormField>
      </div>

      <LoadSpinner v-if="busy.active.value" />

      <div v-if="fill.form.value" class="equip-fill__items q-mt-md">
        <div class="equip-fill__line equip-fill__head q-py-xs">
          <span>{{ t('Позиция') }}</span>
          <span>{{ t('Норма') }}</span>
          <span>{{ t('Требуется') }}</span>
          <span>{{ t('Было') }}</span>
          <span>{{ t('Фактически') }}</span>
        </div>
        <template v-for="{ item, depth } in fill.rows.value" :key="item.id">
          <div
            v-if="item.unit === null"
            class="equip-fill__section q-pt-md q-pb-xs"
            :style="{ paddingLeft: `${depth * 1.2}rem` }"
          >
            {{ textOf(item) }}
          </div>
          <div v-else class="equip-fill__pos q-py-xs" :style="{ paddingLeft: `${depth * 1.2}rem` }">
            <div class="equip-fill__line" :class="{ 'equip-fill__line--bad': fill.violation(item) }">
              <span>
                {{ textOf(item) }}, {{ item.unit }}
                <span v-if="item.critical" class="equip-fill__critical q-ml-sm">{{ t('критичный') }}</span>
                <!-- примечание и фото — у нарушения сразу, у остальных позиций — по ссылке -->
                <SoftLink
                  v-if="!detailsShown(item)"
                  text="Примечание и фото"
                  class="q-ml-sm"
                  @click="opened.add(item.id)"
                />
              </span>
              <span class="zs-sub">{{ item.normText ?? '' }}</span>
              <span>{{ numText(item.quantityRequired) || '—' }}</span>
              <span>{{ numText(item.quantityPrevious) || '—' }}</span>
              <span :class="{ 'equip-fill__decreased': fill.decreased(item) }">
                <TextInput
                  :model-value="fill.quantities.get(item.id) ?? ''"
                  inputmode="decimal"
                  width="6rem"
                  @update:model-value="fill.quantities.set(item.id, $event)"
                />
              </span>
            </div>
            <!-- примечание и фото — в одну строку: фото справа от примечания, в ряд -->
            <div v-if="detailsShown(item)" class="equip-fill__details q-mt-sm">
              <NoteInput v-model="extra(item).comment" :rows="2" placeholder="Примечание" />
              <PhotoPicker
                v-model="extra(item).photos"
                :stored="storedPhotos(item)"
                @remove-stored="(id) => removeStored(item, id)"
              />
            </div>
          </div>
        </template>
      </div>

      <div v-if="fill.form.value" class="equip-fill__footer q-pt-md">
        <span :class="{ 'equip-fill__critical': fill.violationCount.value > 0 }">
          {{ t('Нарушений: {n}', { n: fill.violationCount.value }) }}
        </span>
        <ErrorBox v-if="fill.sendError.value" :error="fill.sendError.value" />
        <q-btn
          unelevated
          no-caps
          color="primary"
          :label="t('Опись окончена')"
          :loading="fill.sending.value"
          @click="finish"
        />
      </div>
    </div>
  </DesktopFrame>
</template>

<script setup lang="ts">
import { computed, reactive } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import type { EquipFormItem } from '@/api/equip'
import { equipPhotoUrl, fetchEquipList } from '@/api/equip'
import type { RequestPhoto } from '@/api/requests'
import type { RequestWagon } from '@/api/wagons'
import { t } from '@/boot/i18n'
import { provideBusy } from '@/shared/busy'
import { numText, textOf } from '@/shared/checks/texts'
import type { EquipFillExtra } from '@/shared/checks/useEquipFill'
import { useEquipFill } from '@/shared/checks/useEquipFill'
import CbList from '@/shared/components/choice/CbList.vue'
import type { ChoiceItem, ChoiceModel } from '@/shared/components/choice/choice'
import ErrorBox from '@/shared/components/ErrorBox.vue'
import FormField from '@/shared/components/form/FormField.vue'
import NoteInput from '@/shared/components/form/NoteInput.vue'
import SoftLink from '@/shared/components/form/SoftLink.vue'
import TextInput from '@/shared/components/form/TextInput.vue'
import LoadSpinner from '@/shared/components/LoadSpinner.vue'
import { dictManager } from '@/shared/dict/DictManager'
import { shownError } from '@/shared/errors'
import PhotoPicker from '@/shared/requests/PhotoPicker.vue'
import { loadCurrentUser } from '@/shared/user'
import DesktopFrame from '../frame/DesktopFrame.vue'
import RequestWagonField from '../requests/RequestWagonField.vue'

/**
 * Составление описи экипировки (Ф-ЭКП-02) на десктопе: шаблон описи и вагон; позиции по разделам с нормой, требуемым
 * количеством и количеством из прошлой описи. Фактическое количество по умолчанию — из прошлой описи; меньше прошлого —
 * подсвечено. «Опись окончена» отправляет опись: Система создаёт одну заявку по недостаче; открывается список описей с
 * выделенной описью. ?edit=<id> — исправление описи автором в течение 10 минут: шаблон и вагон — из описи, значения,
 * примечания и фото — сохранённые. Примечание и фото позиции — у нарушения сразу, у остальных — по ссылке «Примечание и
 * фото».
 */

const router = useRouter()
const busy = provideBusy()
const fill = useEquipFill(busy)
const route = useRoute()
/** исправляемая опись; null — новая опись */
const editId = route.query.edit ? Number(route.query.edit) : null
// id выбранного вагона: очистка в поле вагона снимает вагон описи
const wagonId = computed({
  get: () => fill.wagon.value?.id ?? null,
  set: (v: number | null) => {
    if (v === null) fill.wagon.value = null
  },
})

const back = () => void router.push({ name: 'equip', query: editId !== null ? { id: String(editId) } : {} })

const templateChoices = computed<ChoiceItem[]>(() => fill.templates.value.map((x) => ({ id: x.id, text: textOf(x) })))
const templateChoice = computed({
  get: () => fill.templateId.value,
  set: (v: ChoiceModel) => (fill.templateId.value = typeof v === 'number' ? v : null),
})

// позиции, у которых примечание и фото раскрыты ссылкой
const opened = reactive(new Set<number>())

function extra(item: EquipFormItem): EquipFillExtra {
  return fill.extras.get(item.id)!
}

/** Примечание и фото показаны: нарушение, уже заполнены или раскрыты. */
function detailsShown(item: EquipFormItem): boolean {
  const e = fill.extras.get(item.id)
  if (!e) return false
  return fill.violation(item) || opened.has(item.id) || e.comment !== '' || e.photos.length > 0 || e.stored.length > 0
}

function storedPhotos(item: EquipFormItem): RequestPhoto[] {
  return extra(item).stored.map((id) => ({ id: String(id), url: equipPhotoUrl(id) }))
}

function removeStored(item: EquipFormItem, id: string) {
  const e = extra(item)
  e.stored = e.stored.filter((x) => String(x) !== id)
}

function onWagon(w: RequestWagon) {
  fill.wagon.value = { id: w.id, number: w.number, wagonType: w.wagonType }
}

// опись сохранена — список описей с выделенной описью
async function finish() {
  const res = await fill.submit()
  if (res) await router.push({ name: 'equip', query: { id: String(res.id) } })
}

function retry() {
  if (fill.templates.value.length === 0) void init()
  else void fill.loadForm()
}

// цепочка загрузок — сразу в setup: пользователь, справочник типов вагона, шаблоны описей
async function init() {
  try {
    await busy.run(Promise.all([loadCurrentUser(), dictManager.prepareDict('WagonType')]))
  } catch (e) {
    fill.loadError.value = shownError(e)
    return
  }
  await fill.loadTemplates()
  if (editId === null) return
  try {
    fill.startEdit(await busy.run(fetchEquipList(editId)))
  } catch (e) {
    fill.loadError.value = shownError(e)
  }
}
void init()
</script>

<style scoped lang="scss">
.equip-fill {
  overflow-y: auto;
  height: 100%;
}

.equip-fill__fixed {
  display: flex;
  gap: 1rem;
  font-weight: 600;
}

.equip-fill__subject {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(18rem, 1fr));
  gap: 1rem;
  max-width: 64rem;
}

.equip-fill__items {
  max-width: 64rem;
}

.equip-fill__section {
  border-bottom: 0.0625rem solid $zs-border;
  font-weight: 600;
}

.equip-fill__head {
  border-bottom: 0.0625rem solid $zs-border-row;
  color: $zs-text-muted;
  font-size: 0.8rem;
}

// позиция: строка значений, под ней — примечание и фото; линия снизу — у всей позиции
.equip-fill__pos {
  border-bottom: 0.0625rem solid $zs-border-row;
}

.equip-fill__details {
  display: flex;
  align-items: flex-start;
  gap: 0.5rem;

  // примечание — свободное место, не шире 40rem
  > :first-child {
    flex: 1;
    min-width: 0;
    max-width: 40rem;
  }

  // фото — в ряд, без переноса
  :deep(.photo-picker) {
    flex: none;
    flex-wrap: nowrap;
  }

  // описание — начальной высотой с плитку фото (PhotoPicker, 4.5rem); растягивается вниз
  :deep(.note-input__field) {
    height: 4.5rem;
  }
}

.equip-fill__line {
  display: grid;
  grid-template-columns: minmax(0, 2fr) minmax(0, 1fr) 6rem 6rem 7rem;
  gap: 0.8rem;
  align-items: center;
  color: $zs-text;
  font-size: 0.9rem;

  > :nth-child(3),
  > :nth-child(4) {
    text-align: right;
  }

  // нарушение — красным шрифтом названия позиции, без фона
  &--bad > :first-child {
    color: $zs-error-fg;
  }
}

.equip-fill__decreased :deep(input) {
  color: $zs-error-fg;
  font-weight: 600;
}

.equip-fill__critical {
  color: $zs-error-fg;
  font-size: 0.8rem;
}

.equip-fill__footer {
  display: flex;
  align-items: center;
  gap: 1rem;
  max-width: 64rem;
}
</style>
