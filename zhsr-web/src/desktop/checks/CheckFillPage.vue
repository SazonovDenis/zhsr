<template>
  <DesktopFrame :back="back">
    <template #header>
      <h1 class="desktop-frame__title">
        {{ editId !== null ? t('Изменение чек-листа') : t('Новая проверка') }}
      </h1>
    </template>

    <div class="check-fill q-pa-md">
      <ErrorBox v-if="fill.loadError.value" :error="fill.loadError.value" :retry="retry" class="q-mb-md" />

      <!-- исправление: шаблон и объект проверки — из чек-листа, не меняются -->
      <div v-if="editId !== null" class="check-fill__fixed">
        <span>{{ fill.template.value ? textOf(fill.template.value) : '' }}</span>
        <span v-if="fill.wagon.value" class="zs-mono">{{ fill.wagon.value.number }}</span>
        <span v-else-if="fill.editing.value?.checkedUsr"><UserName :user="fill.editing.value.checkedUsr" /></span>
      </div>
      <div v-else class="check-fill__subject">
        <FormField label="Чек-лист" class="check-fill__field">
          <CbList v-model="templateChoice" single placeholder="Выберите чек-лист" :values="templateChoices" />
        </FormField>

        <template v-if="fill.checkObject.value === CheckObject.WAGON">
          <FormField label="Вагон" class="check-fill__wagon">
            <RequestWagonField v-model="wagonId" @pick="onWagon" />
          </FormField>
        </template>

        <template v-else-if="fill.checkObject.value !== null">
          <FormField label="Рейс" class="check-fill__field">
            <CbList
              v-model="tripChoice"
              single
              placeholder="Выберите рейс"
              :values="tripChoices"
              :source="fill.tripLoad"
            />
          </FormField>
          <FormField v-if="fill.checkObject.value === CheckObject.USR" label="Сотрудник" class="check-fill__field">
            <CbList
              v-model="usrChoice"
              single
              :placeholder="fill.trip.value === null ? 'Сначала выберите рейс' : 'Выберите члена бригады'"
              :values="crewChoices"
              :source="fill.crewLoad"
            >
              <!-- «Список ФИО»: ФИО и под ним должность -->
              <template #option="{ item }">
                <UserOption
                  :name="(item.data?.name as string | undefined) ?? ''"
                  :me="(item.data?.me as boolean | undefined) ?? false"
                  :duty="(item.data?.duty as string | undefined) ?? ''"
                />
              </template>
            </CbList>
          </FormField>
        </template>
      </div>

      <LoadSpinner v-if="busy.active.value" />

      <div v-if="fill.form.value" class="check-fill__items q-mt-md">
        <template v-for="{ item, depth } in fill.rows.value" :key="item.id">
          <div
            v-if="item.valueKind === null"
            class="check-fill__section q-pt-md q-pb-xs"
            :style="{ paddingLeft: `${depth * 1.2}rem` }"
          >
            {{ textOf(item) }}
          </div>
          <div
            v-else
            class="check-fill__item q-py-sm"
            :class="{ 'check-fill__item--bad': fill.violation(item) }"
            :style="{ paddingLeft: `${depth * 1.2}rem` }"
          >
            <div class="check-fill__line">
              <div class="check-fill__text">
                <span>{{ textOf(item) }}</span>
                <span v-if="item.required" class="check-fill__mark">*</span>
                <span v-if="item.critical" class="check-fill__critical q-ml-sm">{{ t('критичный') }}</span>
                <div v-if="hint(item)" class="zs-sub">{{ hint(item) }}</div>
              </div>
              <div class="check-fill__input">
                <CbSegment
                  v-if="item.valueKind === CheckValueKind.DICT_VALUE"
                  v-model="value(item).checkDictValue"
                  single
                  :values="dictChoices(item)"
                  :max-button-count="6"
                />
                <TextInput
                  v-else-if="item.valueKind === CheckValueKind.NUMBER"
                  v-model="value(item).valueNumber"
                  inputmode="decimal"
                  width="8rem"
                />
                <TextInput v-else v-model="value(item).valueString" />
              </div>
              <SoftLink v-if="!detailsShown(item)" text="Описание и фото" @click="opened.add(item.id)" />
            </div>
            <!-- описание и фото — в одну строку: фото справа от описания, в ряд -->
            <div v-if="detailsShown(item)" class="check-fill__details q-mt-sm">
              <NoteInput v-model="value(item).comment" :rows="2" placeholder="Описание нарушения" />
              <PhotoPicker
                v-model="value(item).photos"
                :stored="storedPhotos(item)"
                @remove-stored="(id) => removeStored(item, id)"
              />
            </div>
          </div>
        </template>
      </div>

      <div v-if="fill.form.value" class="check-fill__footer q-pt-md">
        <span :class="{ 'check-fill__critical': fill.violationCount.value > 0 }">
          {{ t('Нарушений: {n}', { n: fill.violationCount.value }) }}
        </span>
        <ErrorBox v-if="fill.sendError.value" :error="fill.sendError.value" />
        <q-btn
          unelevated
          no-caps
          color="primary"
          :label="t('Проверка окончена')"
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

import UserOption from '@/shared/components/UserOption.vue'
import UserName from '@/shared/components/UserName.vue'
import type { CheckFormItem } from '@/api/checks'
import { checkPhotoUrl, CheckObject, CheckValueKind, fetchCheckList } from '@/api/checks'
import type { RequestPhoto } from '@/api/requests'
import type { RequestWagon } from '@/api/wagons'
import { t } from '@/boot/i18n'
import { provideBusy } from '@/shared/busy'
import { numText, textOf } from '@/shared/checks/texts'
import type { CheckFillValue } from '@/shared/checks/useCheckFill'
import { useCheckFill } from '@/shared/checks/useCheckFill'
import CbList from '@/shared/components/choice/CbList.vue'
import CbSegment from '@/shared/components/choice/CbSegment.vue'
import type { ChoiceItem, ChoiceModel } from '@/shared/components/choice/choice'
import ErrorBox from '@/shared/components/ErrorBox.vue'
import NoteInput from '@/shared/components/form/NoteInput.vue'
import FormField from '@/shared/components/form/FormField.vue'
import SoftLink from '@/shared/components/form/SoftLink.vue'
import TextInput from '@/shared/components/form/TextInput.vue'
import LoadSpinner from '@/shared/components/LoadSpinner.vue'
import { dictManager } from '@/shared/dict/DictManager'
import { shownError } from '@/shared/errors'
import PhotoPicker from '@/shared/requests/PhotoPicker.vue'
import { isMe, loadCurrentUser, userName } from '@/shared/user'
import DesktopFrame from '../frame/DesktopFrame.vue'
import RequestWagonField from '../requests/RequestWagonField.vue'

/**
 * Проведение проверки по чек-листу (Ф-ЧЕК-03) на десктопе: чек-лист, объект проверки (вагон; рейс и член бригады;
 * рейс), пункты по разделам. Описание нарушения и фото — у нарушенного пункта или по «Описание и фото». «Проверка
 * окончена» отправляет чек-лист: Система создаёт заявки по нарушениям; открывается список проверок с выделенным
 * чек-листом. ?edit=<id> — исправление чек-листа автором в течение 10 минут: шаблон и объект проверки — из чек-листа,
 * значения, описания и фото — сохранённые.
 */

const router = useRouter()
const busy = provideBusy()
const fill = useCheckFill(busy)
// пункты, у которых раскрыты описание и фото
const opened = reactive(new Set<number>())
// id выбранного вагона: очистка в поле вагона снимает вагон проверки
const wagonId = computed({
  get: () => fill.wagon.value?.id ?? null,
  set: (v: number | null) => {
    if (v === null) fill.wagon.value = null
  },
})

const route = useRoute()
/** исправляемый чек-лист (?edit); null — новый чек-лист */
const editId = route.query.edit ? Number(route.query.edit) : null
const back = () => void router.push({ name: 'checks', query: editId !== null ? { id: String(editId) } : {} })

const templateChoices = computed<ChoiceItem[]>(() => fill.templates.value.map((x) => ({ id: x.id, text: textOf(x) })))
const templateChoice = computed({
  get: () => fill.templateId.value,
  set: (v: ChoiceModel) => (fill.templateId.value = typeof v === 'number' ? v : null),
})
const tripChoices = computed<ChoiceItem[]>(() =>
  fill.trips.value.map((trip) => ({ id: trip.id, text: `${trip.trainNumber} • ${trip.name}` })),
)
const tripChoice = computed({
  get: () => fill.trip.value,
  set: (v: ChoiceModel) => (fill.trip.value = typeof v === 'number' ? v : null),
})
const crewChoices = computed<ChoiceItem[]>(() =>
  fill.crew.value.map((m) => ({
    id: m.usr.id,
    text: `${userName(m.usr)} • ${dictManager.getValue('CrewDuty', m.crewDutyType)}`,
    data: { name: m.usr.name, me: isMe(m.usr), duty: dictManager.getValue('CrewDuty', m.crewDutyType) },
  })),
)
const usrChoice = computed({
  get: () => fill.checkedUsr.value,
  set: (v: ChoiceModel) => (fill.checkedUsr.value = typeof v === 'number' ? v : null),
})

function value(item: CheckFormItem): CheckFillValue {
  return fill.values.get(item.id)!
}

function dictChoices(item: CheckFormItem): ChoiceItem[] {
  return fill.dictValuesOf(item).map((v) => ({ id: v.id, text: textOf(v) }))
}

/** Подсказка норматива: границы числа и допустимые варианты. */
function hint(item: CheckFormItem): string {
  if (item.numMin !== null || item.numMax !== null) {
    return t('Норма: от {min} до {max}', { min: numText(item.numMin) || '…', max: numText(item.numMax) || '…' })
  }
  return ''
}

/** Описание и фото показаны: пункт нарушен, уже заполнены или раскрыты. */
function detailsShown(item: CheckFormItem): boolean {
  const v = value(item)
  return fill.violation(item) || opened.has(item.id) || v.comment !== '' || v.photos.length > 0 || v.stored.length > 0
}

function storedPhotos(item: CheckFormItem): RequestPhoto[] {
  return value(item).stored.map((id) => ({ id: String(id), url: checkPhotoUrl(id) }))
}

function removeStored(item: CheckFormItem, id: string) {
  const v = value(item)
  v.stored = v.stored.filter((x) => String(x) !== id)
}

function onWagon(w: RequestWagon) {
  fill.wagon.value = { id: w.id, number: w.number, wagonType: w.wagonType }
}

// чек-лист сохранён — список проверок с выделенным чек-листом
async function finish() {
  const res = await fill.submit()
  if (res) await router.push({ name: 'checks', query: { id: String(res.id) } })
}

function retry() {
  if (fill.templates.value.length === 0) void init()
  else void fill.loadForm()
}

// цепочка загрузок — сразу в setup: пользователь (права на шаблоны), справочники типов вагона и должностей, шаблоны
async function init() {
  try {
    await busy.run(Promise.all([loadCurrentUser(), dictManager.prepareDicts('WagonType', 'CrewDuty')]))
  } catch (e) {
    fill.loadError.value = shownError(e)
    return
  }
  await fill.loadTemplates()
  if (editId === null) return
  try {
    fill.startEdit(await busy.run(fetchCheckList(editId)))
  } catch (e) {
    fill.loadError.value = shownError(e)
  }
}
void init()
</script>

<style scoped lang="scss">
.check-fill {
  overflow-y: auto;
  height: 100%;
}

// поля объекта проверки — подряд без растяжки: промежуток между полями один, не на всю ширину
.check-fill__fixed {
  display: flex;
  gap: 1rem;
  font-weight: 600;
}

.check-fill__subject {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  gap: 1rem;
  max-width: 64rem;
}

.check-fill__field {
  flex: 0 1 22rem;
  min-width: 0;
}

.check-fill__wagon {
  flex: 0 1 36rem;
  min-width: 0;
}

.check-fill__items {
  max-width: 64rem;
}

.check-fill__section {
  border-bottom: 0.0625rem solid $zs-border;
  font-weight: 600;
}

.check-fill__item {
  border-bottom: 0.0625rem solid $zs-border-row;

  // нарушение — красным шрифтом названия пункта, без фона
  &--bad .check-fill__text > span:first-child {
    color: $zs-error-fg;
  }
}

.check-fill__line {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(12rem, auto) 8rem;
  gap: 1rem;
  align-items: center;
}

.check-fill__text {
  color: $zs-text;
  font-size: 0.9rem;
}

.check-fill__mark {
  color: $zs-error-fg;
}

.check-fill__critical {
  color: $zs-error-fg;
  font-size: 0.8rem;
}

.check-fill__details {
  display: flex;
  align-items: flex-start;
  gap: 0.5rem;

  // описание — всё свободное место
  > :first-child {
    flex: 1;
    min-width: 0;
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

.check-fill__footer {
  display: flex;
  align-items: center;
  gap: 1rem;
  max-width: 64rem;
}
</style>
