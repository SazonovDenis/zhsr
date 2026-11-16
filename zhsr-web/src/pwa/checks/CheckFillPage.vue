<template>
  <!-- «назад» в шапке — выход из чек-листа без сохранения; по шагам назад — кнопка «Назад» внизу -->
  <!-- пункты: серый фон под карточками, прокручивается блок пунктов (fill) — заголовки разделов липнут к его верху -->
  <PwaPage
    :title="editId !== null ? t('Изменение чек-листа') : t('Чек-лист')"
    :subtitle="subtitle"
    :back="exit"
    :white="!itemsShown"
    :fill="itemsShown"
  >
    <LoadSpinner v-if="busy.active.value" />
    <ErrorBox v-if="fill.loadError.value" :error="fill.loadError.value" :retry="retry" />

    <!-- шаг 1: чек-лист -->
    <template v-if="fill.templateId.value === null">
      <div class="pwa-check__label">{{ t('Чек-лист') }}</div>
      <button
        v-for="tpl in fill.templates.value"
        :key="tpl.id"
        type="button"
        class="pwa-check__choice q-px-md"
        @click="fill.templateId.value = tpl.id"
      >
        <span>{{ textOf(tpl) }}</span>
        <span class="pwa-check__muted">{{ checkObjectText(tpl.checkObject) }}</span>
      </button>
      <div
        v-if="!busy.active.value && fill.templates.value.length === 0 && !fill.loadError.value"
        class="pwa-check__muted"
      >
        {{ t('Нет чек-листов, которые вы проводите') }}
      </div>
    </template>

    <!-- шаг 2: объект проверки -->
    <template v-else-if="!fill.form.value">
      <template v-if="fill.checkObject.value === CheckObject.WAGON">
        <div class="pwa-check__label">{{ t('Вагон') }}</div>
        <WagonPickList @pick="onWagon" />
      </template>
      <template v-else>
        <div class="pwa-check__label">{{ t('Рейс') }}</div>
        <CbList
          :model-value="fill.trip.value"
          single
          placeholder="Выберите рейс"
          :values="tripChoices"
          :source="fill.tripLoad"
          class="q-mt-sm"
          @update:model-value="fill.trip.value = typeof $event === 'number' ? $event : null"
        />
        <template v-if="fill.checkObject.value === CheckObject.USR && fill.trip.value !== null">
          <div class="pwa-check__label">{{ t('Сотрудник') }}</div>
          <button
            v-for="m in fill.crew.value"
            :key="m.usr.id"
            type="button"
            class="pwa-check__choice q-px-md"
            @click="fill.checkedUsr.value = m.usr.id"
          >
            <span>{{ userName(m.usr) }}</span>
            <span class="pwa-check__muted">{{ dictManager.getValue('CrewDuty', m.crewDutyType) }}</span>
          </button>
          <ErrorBox v-if="fill.crewLoad.error" :error="fill.crewLoad.error" :retry="fill.crewLoad.retry" />
        </template>
      </template>
    </template>

    <!-- шаг 3: пункты — карточками по разделам верхнего уровня; прокручивается свой блок, шапка страницы стоит -->
    <div v-else class="pwa-check__items">
      <div v-for="card in cards" :key="card.key" class="pwa-check__card q-mb-sm">
        <!-- заголовок раздела прилипает к верху в пределах первой части — всех пунктов, кроме последнего: начинает
             уходить, как только последний пункт доходит до заголовка -->
        <div v-for="(part, pi) in card.parts" :key="pi" class="pwa-check__part">
          <div v-if="pi === 0 && card.head" class="pwa-check__card-head q-px-md q-py-sm">{{ textOf(card.head) }}</div>
          <template v-for="{ item, depth } in part" :key="item.id">
            <!-- вложенность — по 0.5rem на уровень: на телефоне отступ отнимает ширину у текста -->
            <div
              v-if="item.valueKind === null"
              class="pwa-check__sub q-pr-md q-pt-sm q-pb-xs"
              :style="{ paddingLeft: cardIndent(depth) }"
            >
              {{ textOf(item) }}
            </div>
            <div v-else class="pwa-check__row q-pr-md q-py-sm" :style="{ paddingLeft: cardIndent(depth) }">
              <div class="pwa-check__line">
                <div class="pwa-check__text" :class="{ 'pwa-check__text--bad': fill.violation(item) }">
                  {{ textOf(item) }}
                  <span v-if="item.required" class="pwa-check__bad">*</span>
                  <div v-if="item.numMin !== null || item.numMax !== null" class="pwa-check__muted pwa-check__small">
                    {{
                      t('Норма: от {min} до {max}', {
                        min: numText(item.numMin) || '…',
                        max: numText(item.numMax) || '…',
                      })
                    }}
                  </div>
                </div>
                <!-- варианта два — циклическая кнопка справа; больше — кнопки строкой под текстом -->
                <CbCycle
                  v-if="item.valueKind === CheckValueKind.DICT_VALUE && answerInline(item)"
                  v-model="value(item).checkDictValue"
                  class="pwa-check__answer"
                  :class="answerClass(item)"
                  :values="dictChoices(item)"
                />
                <input
                  v-else-if="item.valueKind === CheckValueKind.NUMBER"
                  v-model="value(item).valueNumber"
                  class="pwa-check__input pwa-check__input--number q-px-sm"
                  :class="{ 'pwa-check__input--bad': fill.violation(item) }"
                  inputmode="decimal"
                />
              </div>
              <!-- вариантов больше двух — кнопки под текстом во всю ширину -->
              <CbButton
                v-if="item.valueKind === CheckValueKind.DICT_VALUE && !answerInline(item)"
                v-model="value(item).checkDictValue"
                single
                wrap
                class="pwa-check__answer q-mt-sm"
                :class="answerClass(item)"
                :values="dictChoices(item)"
              />
              <input
                v-else-if="item.valueKind === CheckValueKind.STRING"
                v-model="value(item).valueString"
                class="pwa-check__input q-mt-sm q-px-md"
              />
              <!-- нарушение: примечание и фото; заполненные остаются и без нарушения -->
              <ItemNotePhotos
                v-if="extrasShown(item)"
                v-model:comment="value(item).comment"
                v-model:photos="value(item).photos"
                :stored="storedPhotos(item)"
                class="q-mt-sm"
                @remove-stored="(id) => removeStored(item, id)"
              />
              <div v-if="fill.violation(item) && item.faultType !== null" class="pwa-check__request q-mt-xs">
                {{ t('Будет создана заявка ответственной службе') }}
              </div>
            </div>
          </template>
        </div>
      </div>
    </div>

    <template v-if="fill.form.value" #bar>
      <BottomBar :actions="barActions" @action="(key) => (key === 'back' ? back() : finish())">
        <ErrorBox v-if="fill.sendError.value" :error="fill.sendError.value" />
        <span :class="{ 'pwa-check__bad': fill.violationCount.value > 0 }">
          {{ t('Нарушений: {n}', { n: fill.violationCount.value }) }}
        </span>
      </BottomBar>
    </template>
  </PwaPage>

  <!-- выход из чек-листа без сохранения: введённые значения пропадут -->
  <BottomSheet v-model:show="exitOpen" :title="t('Выйти без сохранения?')">
    <div>{{ t('Чек-лист не окончен: введённые значения не сохранятся.') }}</div>
    <template #buttons>
      <BottomBar :actions="exitActions" @action="(key) => (key === 'stay' ? (exitOpen = false) : void leave())" />
    </template>
  </BottomSheet>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import type { CheckFormItem } from '@/api/checks'
import { checkPhotoUrl, CheckObject, CheckValueKind, fetchCheckList } from '@/api/checks'
import type { RequestPhoto } from '@/api/requests'
import type { RequestWagon } from '@/api/wagons'
import { t } from '@/boot/i18n'
import { provideBusy } from '@/shared/busy'
import { checkObjectText } from '@/shared/checks/labels'
import { numText, textOf } from '@/shared/checks/texts'
import type { CheckFillValue } from '@/shared/checks/useCheckFill'
import { useCheckFill } from '@/shared/checks/useCheckFill'
import { cardIndent, treeCards } from '@/shared/checks/tree'
import CbButton from '@/shared/components/choice/CbButton.vue'
import CbCycle from '@/shared/components/choice/CbCycle.vue'
import CbList from '@/shared/components/choice/CbList.vue'
import type { ChoiceItem } from '@/shared/components/choice/choice'
import ErrorBox from '@/shared/components/ErrorBox.vue'
import LoadSpinner from '@/shared/components/LoadSpinner.vue'
import { dictManager } from '@/shared/dict/DictManager'
import { shownError } from '@/shared/errors'
import { loadCurrentUser, userName } from '@/shared/user'
import type { BottomBarAction } from '../components/BottomBar.vue'
import BottomBar from '../components/BottomBar.vue'
import BottomSheet from '../components/BottomSheet.vue'
import ItemNotePhotos from '../components/ItemNotePhotos.vue'
import PwaPage from '../components/PwaPage.vue'
import WagonPickList from '../components/WagonPickList.vue'
import { closeSheet, exitFill, leaveFill } from './wizardHistory'

/**
 * Проведение проверки по чек-листу на телефоне (Ф-ЧЕК-03): чек-лист, объект проверки, пункты карточками по разделам
 * верхнего уровня; ответ из двух вариантов — циклической кнопкой справа от текста, из большего числа — кнопками под
 * текстом. У нарушенного пункта подряд — ссылки «Добавить примечание» (поле
 * открывается по нажатию, вне поля примечание — текстом с крестиком удаления) и «Добавить фото» (сразу камера; снятое
 * фото — миниатюры с просмотром). Только при связи: значения живут в памяти страницы и уходят на сервер один раз по
 * «Проверка окончена», после чего открывается просмотр сохранённого чек-листа. «Назад» возвращает на шаг назад, с
 * первого шага — на главную.
 */

const router = useRouter()
const busy = provideBusy()
const fill = useCheckFill(busy)

/**
 * Шаблон и вагон, выбранные в мастере начала (CheckStartPage): ?template, а у чек-листа вагона — ?wagon, ?number,
 * ?wagonType. Заданы — сразу пункты; «Назад» с пунктов возвращает в мастер.
 */
const route = useRoute()
const preset = route.query.template !== undefined
/** исправляемый чек-лист (?edit); null — новый чек-лист */
const editId = route.query.edit ? Number(route.query.edit) : null

function applyPreset() {
  const tpl = Number(route.query.template)
  if (!tpl) return
  fill.templateId.value = tpl
  const id = Number(route.query.wagon)
  if (id) fill.wagon.value = { id, number: String(route.query.number ?? ''), wagonType: Number(route.query.wagonType) }
}

const itemsShown = computed(() => !!fill.form.value)

const cards = computed(() => treeCards(fill.rows.value, (item) => item.valueKind === null))

const subtitle = computed(() => (fill.template.value ? textOf(fill.template.value) : ''))

const tripChoices = computed<ChoiceItem[]>(() =>
  fill.trips.value.map((tr) => ({ id: tr.id, text: `${tr.trainNumber} • ${tr.name}` })),
)

function value(item: CheckFormItem): CheckFillValue {
  return fill.values.get(item.id)!
}

function dictChoices(item: CheckFormItem): ChoiceItem[] {
  return fill.dictValuesOf(item).map((v) => ({ id: v.id, text: textOf(v) }))
}

/** Ответ — циклической кнопкой справа от текста: вариантов не больше двух («В порядке» и «Нарушение»). */
function answerInline(item: CheckFormItem): boolean {
  return fill.dictValuesOf(item).length <= 2
}

/** Цвет выбранного ответа: нарушение — красный, допустимый по нормативу — зелёный, без норматива — обычный. */
function answerClass(item: CheckFormItem): string {
  if (value(item).checkDictValue === null) return ''
  if (fill.violation(item)) return 'pwa-check__answer--bad'
  return item.checkListItemNorm !== null && item.checkDictValues.length > 0 ? 'pwa-check__answer--ok' : ''
}

/** Описание и фото — у нарушенного пункта и у заполненных. */
function extrasShown(item: CheckFormItem): boolean {
  const v = value(item)
  return fill.violation(item) || v.comment !== '' || v.photos.length > 0 || v.stored.length > 0
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

const barActions = computed<BottomBarAction[]>(() => [
  { key: 'back', title: 'Назад', align: 'left' },
  { key: 'finish', title: 'Проверка окончена', kind: 'primary', loading: fill.sending.value },
])

// «Отменить и выйти» — слева, красная; «Продолжить» заполнение — прижата вправо, основная
const exitActions: BottomBarAction[] = [
  { key: 'leave', title: 'Отменить и выйти', kind: 'danger', align: 'left', stretch: false },
  { key: 'stay', title: 'Продолжить', kind: 'primary', stretch: false },
]

// «назад» в шапке: выход в список описей и чек-листов; чек-лист начат и не окончен — сначала предупреждение
const exitOpen = ref(false)

function exit() {
  if (fill.form.value) exitOpen.value = true
  else void leave()
}

/**
 * Выйти без сохранения. Исправление — назад в просмотр чек-листа; новый — туда, откуда открыли мастер или заполнение:
 * ни мастер, ни заполнение в истории не остаются. Открыт лист подтверждения — сначала он закрывается и снимает свою
 * запись из истории.
 */
async function leave() {
  if (exitOpen.value) await closeSheet(() => (exitOpen.value = false))
  if (editId !== null) await router.replace({ name: 'pwa-check-list', params: { id: String(editId) } })
  else await exitFill(router, { name: 'pwa-checks' })
}

/** «Назад» внизу: с пунктов — к объекту проверки (из мастера — в мастер), с объекта — к выбору чек-листа, с первого
 * шага — в список. */
function back() {
  if (editId !== null) {
    void leave()
  } else if (fill.templateId.value === null) {
    void router.push({ name: 'pwa-checks' })
  } else if (preset && fill.form.value) {
    router.back()
  } else if (fill.form.value) {
    fill.wagon.value = null
    fill.checkedUsr.value = null
    if (fill.checkObject.value === CheckObject.TRIP) fill.trip.value = null
  } else {
    fill.templateId.value = null
  }
}

// чек-лист сохранён — его просмотр вместо заполнения и мастера: «назад» из просмотра не возвращает ни в заполнение, ни в мастер
async function finish() {
  const res = await fill.submit()
  if (res) await leaveFill(router, { name: 'pwa-check-list', params: { id: String(res.id) } })
}

function retry() {
  if (fill.templates.value.length === 0) void init()
  else void fill.loadForm()
}

// цепочка загрузок — сразу в setup: пользователь (права на шаблоны), справочники, шаблоны
async function init() {
  try {
    await busy.run(Promise.all([loadCurrentUser(), dictManager.prepareDicts('WagonType', 'CrewDuty')]))
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
    fill.startEdit(await busy.run(fetchCheckList(editId)))
  } catch (e) {
    fill.loadError.value = shownError(e)
  }
}
void init()
</script>

<style scoped lang="scss">
// цвета проверки: в порядке, нарушение, будущая заявка
$zs-check-ok-bg: #dcfce7;
$zs-check-ok-fg: #15803d;
$zs-check-bad: #e0001b;
$zs-check-request: #c2410c;

.pwa-check__label {
  margin-top: 0.5rem;
  font-size: 0.9rem;
  font-weight: 600;
}

.pwa-check__muted {
  color: $zs-text-muted;
  font-size: 0.9rem;
}

.pwa-check__small {
  font-size: 0.8rem;
}

.pwa-check__bad {
  color: $zs-error-fg;
}

.pwa-check__choice {
  display: flex;
  flex-direction: column;
  justify-content: center;
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

.pwa-check__input {
  width: 100%;
  height: 2.8rem;
  border: 0.0625rem solid $zs-border-input;
  border-radius: 0.5rem;
  background: #fff;
  font: inherit;
  font-size: 1rem;

  &--number {
    flex: none;
    width: 6rem;
    text-align: right;
  }

  &--bad {
    border-color: $zs-check-bad;
    color: $zs-check-bad;
  }
}

.pwa-check__items {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
}

.pwa-check__card {
  border: 0.0625rem solid $zs-border;
  border-radius: 0.8rem;
  background: #fff;
}

.pwa-check__card-head {
  position: sticky;
  top: 0;
  z-index: 1;
  border-bottom: 0.0625rem solid $zs-border-row;
  border-radius: 0.8rem 0.8rem 0 0;
  background: $zs-bg-header;
  font-weight: 700;
}

.pwa-check__sub {
  font-size: 0.9rem;
  font-weight: 600;
}

.pwa-check__row {
  border-bottom: 0.0625rem solid $zs-border-row;
}

// последний пункт карточки — без линии снизу
.pwa-check__part:last-child > .pwa-check__row:last-child {
  border-bottom: 0;
}

.pwa-check__line {
  display: flex;
  align-items: center;
  gap: 0.8rem;
}

.pwa-check__text {
  flex: 1;
  min-width: 0;

  &--bad {
    color: $zs-check-bad;
  }
}

.pwa-check__answer {
  flex: none;
}

// цвета выбранного ответа: в порядке — зелёный, нарушение — красный; CbCycle — сама кнопка, CbButton — кнопки в нём
.pwa-check__answer--ok.cb-cycle--selected,
.pwa-check__answer--ok :deep(.cb-button__btn--selected) {
  border-color: $zs-check-ok-bg;
  background: $zs-check-ok-bg !important;
  color: $zs-check-ok-fg !important;
}

.pwa-check__answer--bad.cb-cycle--selected,
.pwa-check__answer--bad :deep(.cb-button__btn--selected) {
  border-color: $zs-check-bad;
  background: $zs-check-bad !important;
  color: #fff !important;
}

.pwa-check__request {
  color: $zs-check-request;
  font-size: 0.8rem;
}
</style>
