<template>
  <!-- «назад» в шапке — выход из описи без сохранения; по шагам назад — кнопка «Назад» внизу -->
  <!-- позиции: серый фон под карточками, прокручивается блок позиций (fill) — заголовки разделов липнут к его верху -->
  <PwaPage
    :title="fill.editing.value ? t('Изменение описи') : t('Опись экипировки')"
    :subtitle="subtitle"
    :back="exit"
    :white="!itemsShown"
    :fill="itemsShown"
  >
    <LoadSpinner v-if="busy.active.value" />
    <ErrorBox v-if="fill.loadError.value" :error="fill.loadError.value" :retry="retry" />

    <!-- шаг 1: шаблон описи -->
    <template v-if="fill.templateId.value === null">
      <div class="pwa-equip__label">{{ t('Шаблон описи') }}</div>
      <button
        v-for="tpl in fill.templates.value"
        :key="tpl.id"
        type="button"
        class="pwa-equip__choice q-px-md"
        @click="fill.templateId.value = tpl.id"
      >
        {{ textOf(tpl) }}
      </button>
    </template>

    <!-- шаг 2: вагон -->
    <template v-else-if="!fill.form.value">
      <div class="pwa-equip__label">{{ t('Вагон') }}</div>
      <WagonPickList @pick="onWagon" />
    </template>

    <!-- шаг 3: позиции — карточками по разделам верхнего уровня, как в чек-листе; прокручивается свой блок -->
    <div v-else class="pwa-equip__items">
      <div v-for="card in cards" :key="card.key" class="pwa-equip__card q-mb-sm">
        <!-- заголовок раздела липнет к верху в пределах всех позиций, кроме последней (см. treeCards) -->
        <div v-for="(part, pi) in card.parts" :key="pi" class="pwa-equip__part">
          <div v-if="pi === 0 && card.head" class="pwa-equip__card-head q-px-md q-py-sm">{{ textOf(card.head) }}</div>
          <template v-for="{ item, depth } in part" :key="item.id">
            <div
              v-if="item.unit === null"
              class="pwa-equip__sub q-pr-md q-pt-sm q-pb-xs"
              :style="{ paddingLeft: cardIndent(depth) }"
            >
              {{ textOf(item) }}
            </div>
            <div v-else class="pwa-equip__row q-pr-md q-py-sm" :style="{ paddingLeft: cardIndent(depth) }">
              <div class="pwa-equip__line">
                <div class="pwa-equip__text" :class="{ 'pwa-equip__text--bad': fill.violation(item) }">
                  {{ textOf(item) }}, {{ item.unit }}
                  <div class="pwa-equip__muted pwa-equip__small">
                    {{ t('Требуется: {n}', { n: numText(item.quantityRequired) || '—' }) }} •
                    {{ t('Было: {n}', { n: numText(item.quantityPrevious) || '—' }) }}
                  </div>
                </div>
                <input
                  :value="fill.quantities.get(item.id) ?? ''"
                  class="pwa-equip__input q-px-sm"
                  :class="{
                    'pwa-equip__input--bad': fill.violation(item),
                    'pwa-equip__input--less': fill.decreased(item),
                  }"
                  inputmode="decimal"
                  @input="fill.quantities.set(item.id, ($event.target as HTMLInputElement).value)"
                />
              </div>
              <!-- нарушение: примечание и фото; заполненные остаются и без нарушения -->
              <ItemNotePhotos
                v-if="extrasShown(item)"
                v-model:comment="extra(item).comment"
                v-model:photos="extra(item).photos"
                :stored="storedPhotos(item)"
                class="q-mt-sm"
                @remove-stored="(id) => removeStored(item, id)"
              />
            </div>
          </template>
        </div>
      </div>
    </div>

    <template v-if="fill.form.value" #bar>
      <BottomBar :actions="barActions" @action="(key) => (key === 'back' ? back() : finish())">
        <ErrorBox v-if="fill.sendError.value" :error="fill.sendError.value" />
        <span :class="{ 'pwa-equip__bad': fill.violationCount.value > 0 }">
          {{ t('Нарушений: {n}', { n: fill.violationCount.value }) }}
        </span>
      </BottomBar>
    </template>
  </PwaPage>

  <!-- выход из описи без сохранения: введённые количества пропадут -->
  <BottomSheet v-model:show="exitOpen" :title="t('Выйти без сохранения?')">
    <div>{{ t('Опись не окончена: введённые количества не сохранятся.') }}</div>
    <template #buttons>
      <BottomBar :actions="exitActions" @action="(key) => (key === 'stay' ? (exitOpen = false) : void leave())" />
    </template>
  </BottomSheet>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import type { EquipFormItem } from '@/api/equip'
import { equipPhotoUrl, fetchEquipList } from '@/api/equip'
import type { RequestPhoto } from '@/api/requests'
import type { RequestWagon } from '@/api/wagons'
import { t } from '@/boot/i18n'
import { provideBusy } from '@/shared/busy'
import { numText, textOf } from '@/shared/checks/texts'
import { cardIndent, treeCards } from '@/shared/checks/tree'
import type { EquipFillExtra } from '@/shared/checks/useEquipFill'
import { useEquipFill } from '@/shared/checks/useEquipFill'
import ErrorBox from '@/shared/components/ErrorBox.vue'
import LoadSpinner from '@/shared/components/LoadSpinner.vue'
import { dictManager } from '@/shared/dict/DictManager'
import { shownError } from '@/shared/errors'
import { loadCurrentUser } from '@/shared/user'
import type { BottomBarAction } from '../components/BottomBar.vue'
import BottomBar from '../components/BottomBar.vue'
import BottomSheet from '../components/BottomSheet.vue'
import ItemNotePhotos from '../components/ItemNotePhotos.vue'
import PwaPage from '../components/PwaPage.vue'
import WagonPickList from '../components/WagonPickList.vue'
import { closeSheet, exitFill, leaveFill } from './wizardHistory'

/**
 * Составление описи экипировки на телефоне (Ф-ЭКП-02): шаблон описи, вагон, позиции карточками по разделам верхнего
 * уровня — как в чек-листе; у позиции — требуемое количество, количество из прошлой описи и справа поле количества;
 * у позиции с нарушением — примечание и фото, как в чек-листе. Фактическое количество по умолчанию — из прошлой описи;
 * меньше прошлого — подсвечено. Только при связи; «Опись окончена» отправляет опись и открывает её просмотр.
 * ?edit=<id> — исправление сохранённой описи автором в течение 10 минут: шаблон, вагон, значения, примечания и фото —
 * из описи; «назад» возвращает в просмотр.
 */

const router = useRouter()
const busy = provideBusy()
const fill = useEquipFill(busy)

/**
 * Шаблон и вагон, выбранные в мастере начала (CheckStartPage): ?template, и вагон — ?wagon, ?number,
 * ?wagonType. Заданы — сразу пункты; «Назад» с пунктов возвращает в мастер.
 */
const route = useRoute()
const preset = route.query.template !== undefined

function applyPreset() {
  const tpl = Number(route.query.template)
  if (!tpl) return
  fill.templateId.value = tpl
  const id = Number(route.query.wagon)
  if (id) fill.wagon.value = { id, number: String(route.query.number ?? ''), wagonType: Number(route.query.wagonType) }
}

/** исправляемая опись; null — новая опись */
const editId = route.query.edit ? Number(route.query.edit) : null

const itemsShown = computed(() => !!fill.form.value)
const cards = computed(() => treeCards(fill.rows.value, (item) => item.unit === null))

const subtitle = computed(() =>
  [fill.template.value ? textOf(fill.template.value) : '', fill.wagon.value?.number ?? ''].filter(Boolean).join(' • '),
)

function extra(item: EquipFormItem): EquipFillExtra {
  return fill.extras.get(item.id)!
}

/** Примечание и фото — у позиции с нарушением и у заполненных. */
function extrasShown(item: EquipFormItem): boolean {
  const e = fill.extras.get(item.id)
  return fill.violation(item) || (!!e && (e.comment !== '' || e.photos.length > 0 || e.stored.length > 0))
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

const barActions = computed<BottomBarAction[]>(() => [
  { key: 'back', title: 'Назад', align: 'left' },
  { key: 'finish', title: 'Опись окончена', kind: 'primary', loading: fill.sending.value },
])

// «Отменить и выйти» — слева, красная; «Продолжить» заполнение — прижата вправо, основная
const exitActions: BottomBarAction[] = [
  { key: 'leave', title: 'Отменить и выйти', kind: 'danger', align: 'left', stretch: false },
  { key: 'stay', title: 'Продолжить', kind: 'primary', stretch: false },
]

// «назад» в шапке: выход в список описей и чек-листов; опись начата и не окончена — сначала предупреждение
const exitOpen = ref(false)

function exit() {
  if (fill.form.value) exitOpen.value = true
  else void leave()
}

/**
 * Выйти без сохранения. Исправление — назад в просмотр описи; новый — туда, откуда открыли мастер или заполнение:
 * ни мастер, ни заполнение в истории не остаются. Открыт лист подтверждения — сначала он закрывается и снимает свою
 * запись из истории.
 */
async function leave() {
  if (exitOpen.value) await closeSheet(() => (exitOpen.value = false))
  if (editId !== null) await router.replace({ name: 'pwa-equip-list', params: { id: String(editId) } })
  else await exitFill(router, { name: 'pwa-checks' })
}

/** «Назад» внизу: на шаг назад — к вагону, к шаблону (из мастера — в мастер), с первого шага — в список. */
function back() {
  if (editId !== null) void leave()
  else if (fill.templateId.value === null) void router.push({ name: 'pwa-checks' })
  else if (preset && fill.form.value) router.back()
  else if (fill.form.value) fill.wagon.value = null
  else fill.templateId.value = null
}

// опись сохранена — её просмотр вместо заполнения и мастера: «назад» из просмотра не возвращает ни в заполнение, ни в мастер
async function finish() {
  const res = await fill.submit()
  if (res) await leaveFill(router, { name: 'pwa-equip-list', params: { id: String(res.id) } })
}

function retry() {
  if (fill.templates.value.length === 0) void init()
  else void fill.loadForm()
}

async function init() {
  try {
    await busy.run(Promise.all([loadCurrentUser(), dictManager.prepareDict('WagonType')]))
  } catch (e) {
    fill.loadError.value = shownError(e)
    return
  }
  await fill.loadTemplates()
  if (editId === null) {
    applyPreset()
    return
  }
  try {
    fill.startEdit(await busy.run(fetchEquipList(editId)))
  } catch (e) {
    fill.loadError.value = shownError(e)
  }
}
void init()
</script>

<style scoped lang="scss">
// нарушение — тот же красный, что в чек-листе
$zs-equip-bad: #e0001b;

.pwa-equip__label {
  margin-top: 0.5rem;
  font-size: 0.9rem;
  font-weight: 600;
}

.pwa-equip__muted {
  color: $zs-text-muted;
  font-size: 0.9rem;
}

.pwa-equip__small {
  font-size: 0.8rem;
}

.pwa-equip__bad {
  color: $zs-error-fg;
}

.pwa-equip__choice {
  width: 100%;
  min-height: 3.3rem;
  margin-top: 0.5rem;
  border: 0.0625rem solid $zs-border;
  border-radius: 0.5rem;
  background: #fff;
  color: $zs-text;
  font: inherit;
  text-align: left;
}

.pwa-equip__items {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
}

.pwa-equip__card {
  border: 0.0625rem solid $zs-border;
  border-radius: 0.8rem;
  background: #fff;
}

.pwa-equip__card-head {
  position: sticky;
  top: 0;
  z-index: 1;
  border-bottom: 0.0625rem solid $zs-border-row;
  border-radius: 0.8rem 0.8rem 0 0;
  background: $zs-bg-header;
  font-weight: 700;
}

.pwa-equip__sub {
  font-size: 0.9rem;
  font-weight: 600;
}

.pwa-equip__row {
  border-bottom: 0.0625rem solid $zs-border-row;
}

// последняя позиция карточки — без линии снизу
.pwa-equip__part:last-child > .pwa-equip__row:last-child {
  border-bottom: 0;
}

.pwa-equip__line {
  display: flex;
  align-items: center;
  gap: 0.8rem;
}

.pwa-equip__text {
  flex: 1;
  min-width: 0;

  &--bad {
    color: $zs-equip-bad;
  }
}

.pwa-equip__input {
  flex: none;
  width: 6rem;
  height: 2.8rem;
  border: 0.0625rem solid $zs-border-input;
  border-radius: 0.5rem;
  background: #fff;
  font: inherit;
  font-size: 1.1rem;
  text-align: right;

  &--bad {
    border-color: $zs-equip-bad;
  }

  &--less {
    color: $zs-equip-bad;
    font-weight: 600;
  }
}
</style>
