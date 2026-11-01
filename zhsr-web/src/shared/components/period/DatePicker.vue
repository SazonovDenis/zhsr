<template>
  <!-- Единый корень — без него группа кнопок и «...» попадают в DOM родителя как независимые узлы -->
  <div class="date-picker">
    <span v-if="label" class="date-picker__label">{{ t(label) }}</span>

    <!-- дата — первой: всегда видна и вводится вручную: дд.мм.гггг (с временем — дд.мм.гггг чч:мм).
         Ширина поля постоянная: при наборе не меняется -->
    <input
      :value="typed"
      class="date-picker__input zs-focusable"
      :class="{ 'date-picker__input--time': withTime }"
      :placeholder="placeholder"
      inputmode="numeric"
      @focus="startTyping"
      @input="onTypedInput"
      @blur="commitTyped"
      @keydown.enter.prevent="commitTyped"
      @keydown.esc="onEsc"
    />

    <!-- короткий ряд быстрых вариантов: свои кнопки, а не q-btn-toggle — повторный клик при clearable очищает дату.
         Быстрые варианты и «...» Tab пропускает: в поле с вводом Tab сразу уходит к следующему полю, стрелки работают
         только в тексте, а выбор — мышью -->
    <div v-if="shortOptions.length > 0" class="date-picker__short">
      <q-btn
        v-for="option in shortOptions"
        :key="option.value"
        no-caps
        unelevated
        :class="option.value === shortModel ? 'bg-selected text-selected' : 'date-picker__btn'"
        :label="option.label"
        :tabindex="-1"
        @click="onShortClick(option.value)"
      />
    </div>

    <q-btn flat dense icon="more_horiz" class="date-picker__more" :tabindex="-1" @click="open" />

    <!-- anchor/self по центру: при нехватке места Quasar не обрезает окно, а сдвигает его, чтобы вписать в экран.
         max-height: меню Quasar по умолчанию не выше 65vh, и календарь с двумя циферблатами времени в него не
         помещался — появлялась вертикальная прокрутка; теперь окно может занять почти весь экран по высоте -->
    <q-popup-proxy
      ref="popup"
      no-parent-event
      max-height="calc(100vh - 1rem)"
      class="period-picker-popup"
      :breakpoint="SCALE0_MAX_PX"
      anchor="bottom middle"
      self="top middle"
      @show="onPopupShow"
      @hide="onPopupHide"
    >
      <q-card :class="cardClass">
        <div class="row no-wrap" :class="{ 'q-gutter-md': !isCompact() }">
          <!-- Быстрые варианты -->
          <div
            v-if="quickPanelShown"
            :class="{ 'full-width quick-panel-compact': isCompact() }"
            style="white-space: nowrap"
          >
            <!-- Телефон: закрыть выпадающий блок -->
            <div v-if="isFullScreen()" class="row justify-end q-mb-sm">
              <q-btn dense flat icon="close" color="grey-7" @click="close" />
            </div>

            <q-btn-toggle
              :model-value="innerCode"
              :options="quickOptions"
              class="column date-picker__quick"
              toggle-color="selected"
              toggle-text-color="selected"
              spread
              no-caps
              unelevated
              @update:model-value="onQuickUpdate"
            />

            <!-- Мобильный: переход к календарю -->
            <template v-if="isCompact()">
              <q-separator class="full-width q-my-md" />
              <q-btn flat no-caps class="full-width" :label="t('Указать дату...')" @click="showCalendar" />
            </template>
          </div>

          <div
            v-if="calendarShown"
            class="date-picker__calendar"
            :class="{ 'date-picker__calendar--bottom': !applyImmediately && !isFullScreen() }"
          >
            <!-- Мобильный: возврат от календаря к быстрым вариантам -->
            <div v-if="isFullScreen() && quick.length > 0" class="period-back">
              <q-icon class="q-px-xs q-pt-sm" name="arrow_back" size="md" color="grey-7" @click.stop="hideCalendar" />
            </div>
            <div v-else-if="isFullScreen()" class="row justify-end full-width q-mb-sm">
              <q-btn dense flat icon="close" color="grey-7" @click="close" />
            </div>

            <!-- общая шапка календаря и времени: подпись поля и «Очистить», год, дата со временем -->
            <div class="date-picker__header" :class="{ 'date-picker__header--empty': dateInner === null }">
              <div class="row items-center justify-between no-wrap">
                <div class="date-picker__header-text">{{ t(dateText) }}</div>
                <q-btn
                  v-if="nullable && dateInner"
                  dense
                  flat
                  no-caps
                  icon-right="close"
                  color="grey-6"
                  :label="t('Очистить')"
                  @click="onDateUpdate(null)"
                />
              </div>
              <div class="date-picker__header-year">{{ formatYearHeader(dateInner) }}</div>
              <div class="date-picker__header-title">{{ headerTitle }}</div>
            </div>

            <div class="row date-picker__panels" :class="{ 'no-wrap': !isCompact() }">
              <!-- собственную шапку календаря не показываем: шапка общая (minimal) -->
              <q-date
                class="custom-date relative-position"
                :class="{ 'custom-date-mobile': isCompact() }"
                :model-value="dateInner"
                :mask="DATE_FORMAT"
                :no-unset="!nullable"
                minimal
                flat
                @update:model-value="onDateUpdate"
              />

              <!-- время — двумя циферблатами в цветах календаря: часы (0–11 снаружи, 12–23 внутри) и минуты -->
              <div v-if="withTime" class="time-grid" :class="{ 'time-grid--disabled': dateInner === null }">
                <!-- два циферблата одного размера рядом: часы, справа минуты (с точностью до минуты) -->
                <div class="time-grid__body">
                  <div
                    v-for="dial in TIME_DIALS"
                    :key="dial.kind"
                    class="time-dial"
                    @pointerdown="onDialDown($event, dial.cells)"
                    @pointermove="onDialMove($event, dial.cells)"
                    @pointerup="onDialUp"
                    @pointercancel="onDialUp"
                  >
                    <span v-if="hand(dial.kind)" class="time-dial__hand" :style="hand(dial.kind)!" />
                    <span class="time-dial__center" />
                    <button
                      v-for="cell in dial.cells"
                      :key="cell.value"
                      type="button"
                      class="time-dial__cell"
                      :class="{
                        'time-dial__cell--active': isTimeActive(cell),
                        'time-dial__cell--inner': cell.inner,
                      }"
                      :style="cellStyle(cell)"
                      :disabled="dateInner === null"
                      tabindex="-1"
                    >
                      {{ cell.label }}
                    </button>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- Применение -->
        <div v-if="okButtonShown" class="period-apply">
          <q-btn no-caps unelevated color="primary" :label="t(okButtonText || 'Ок')" @click="apply" />
        </div>
      </q-card>
    </q-popup-proxy>
  </div>
</template>

<script setup lang="ts">
import type { QPopupProxy } from 'quasar'
import { date } from 'quasar'
import { computed, onBeforeUnmount, ref } from 'vue'

import { i18n, t } from '@/boot/i18n'
import type { HistoryDialog } from '@/shared/components/dialog/dialogHistory'
import { dialogHistory } from '@/shared/components/dialog/dialogHistory'
import { SCALE0_MAX_PX, screenScale, screenScaleMin } from '@/shared/screen'
import type { QuickDate } from './quickDates'
import { QUICK_DATE_LABELS, quickDateValue } from './quickDates'

/**
 * Выбор даты (и времени, если withTime) — в оформлении и с поведением PeriodPicker: рамка-пилюля с подписью, поле
 * даты, короткий ряд быстрых вариантов, «...» и окно с полным списком быстрых вариантов и календарём.
 *
 *   <DatePicker v-model="since" :quick="['today', 'yesterday', 'monthStart']" />
 *   <DatePicker v-model="planned" with-time :quick="['today', 'tomorrow']" />
 *
 * - Значение — ISO: дата «2026-10-30», с временем — «2026-10-30T21:00». Пусто — null, а не сегодня.
 * - Дата всегда видна в рамке в поле ввода и вводится вручную: дд.мм.гггг (с временем — дд.мм.гггг чч:мм). Можно
 *   печатать и с разделителями, и одни цифры — разделители встают сами. Полностью набранная дата применяется сразу,
 *   неполная — по Enter или при уходе из поля; ошибка в дате — поле возвращается к прежнему значению.
 * - quick — быстрые варианты (см. quickDates.ts); их набор задаёт место применения. Первые shortCount вариантов
 *   стоят кнопками в рамке и применяются сразу. Выбранная кнопка подсвечена: вариант выводится из самой даты.
 * - С временем быстрый вариант меняет только дату, время сохраняется.
 * - В окне выбор применяется кнопкой «Ок» (или сразу при applyImmediately). На мобильном окно — одна панель: быстрые
 *   варианты применяются сразу, «Указать дату...» переходит к календарю.
 * - На телефоне окно раскрыто во весь экран, со своими крестиком и стрелкой возврата; кнопка «назад» сворачивает
 *   выбор по шагам (общий стек dialogHistory).
 */

const DATE_FORMAT = 'DD.MM.YYYY'
const ISO_DATE = 'YYYY-MM-DD'

const props = withDefaults(
  defineProps<{
    /** подпись внутри рамки перед кнопками — на русском */
    label?: string
    /** подпись в шапке календаря — на русском */
    dateText?: string
    withTime?: boolean
    nullable?: boolean
    /** повторный клик по выбранной кнопке короткого ряда очищает значение */
    clearable?: boolean
    applyImmediately?: boolean
    okButtonText?: string
    quick?: QuickDate[]
    /** сколько быстрых вариантов стоит кнопками в рамке */
    shortCount?: number
  }>(),
  {
    label: '',
    dateText: 'Дата',
    withTime: false,
    nullable: true,
    clearable: false,
    applyImmediately: false,
    okButtonText: '',
    quick: () => [],
    shortCount: 2,
  },
)

const model = defineModel<string | null>({ default: null })

const popup = ref<QPopupProxy | null>(null)

// внутреннее значение окна: дата DD.MM.YYYY и время HH:mm; наружу уходит только по «Ок»
const dateInner = ref<string | null>(null)
const timeInner = ref<string | null>(null)

// только для мобильного: какая из двух панелей сейчас видна
const calendarsShown = ref(false)

/** ISO → внутренние дата и время. */
function split(iso: string | null): [string | null, string | null] {
  if (!iso) return [null, null]
  const day = date.formatDate(date.extractDate(iso.slice(0, 10), ISO_DATE), DATE_FORMAT)
  return [day, iso.length > 10 ? iso.slice(11, 16) : null]
}

/** Внутренние дата и время → ISO. Дата без часового пояса: toISOString сдвинул бы её. */
function join(day: string | null, time: string | null): string | null {
  if (!day) return null
  const iso = date.formatDate(date.extractDate(day, DATE_FORMAT), ISO_DATE)
  return props.withTime ? `${iso}T${time ?? '00:00'}` : iso
}

// --- раскладка, как у PeriodPicker ---

function isCompact(): boolean {
  return screenScale() < 3
}

function isFullScreen(): boolean {
  return screenScaleMin() === 0
}

const cardClass = computed(() =>
  isFullScreen() ? 'relative-position' : 'q-pa-md relative-position period-card-padded',
)

// На мобильном помещается одна панель: быстрые варианты или календарь. Без быстрых вариантов — сразу календарь
const quickPanelShown = computed(() => props.quick.length > 0 && (!isCompact() || !calendarsShown.value))
const calendarShown = computed(() => !isCompact() || calendarsShown.value || props.quick.length === 0)

// Быстрый вариант на мобильной панели применяется сразу — кнопка «Ок» там не нужна
const quickPanelApplies = computed(() => isCompact() && quickPanelShown.value)
const okButtonShown = computed(() => !props.applyImmediately && !quickPanelApplies.value)

// --- быстрые варианты ---

const quickOptions = computed(() => props.quick.map((code) => ({ label: t(QUICK_DATE_LABELS[code]), value: code })))

/** Быстрый вариант, которому отвечает дата DD.MM.YYYY; не отвечает ни одному — null. */
function codeOf(day: string | null): QuickDate | null {
  if (!day) return null
  const value = date.extractDate(day, DATE_FORMAT).getTime()
  return props.quick.find((code) => quickDateValue(code).getTime() === value) ?? null
}

const shortCodes = computed(() => props.quick.slice(0, props.shortCount))

// Выбранная кнопка короткого ряда выводится из самой даты: значение может прийти и снаружи
const shortModel = computed(() => {
  const [day] = split(model.value)
  const code = codeOf(day)
  return code && shortCodes.value.includes(code) ? code : null
})

// Короткий ряд: первые быстрые варианты; сама дата — в поле ввода рядом
const shortOptions = computed(() =>
  shortCodes.value.map((code) => ({ label: t(QUICK_DATE_LABELS[code]), value: code })),
)

const innerCode = computed(() => codeOf(dateInner.value))

// --- значение в рамке ---

/** Текст поля для значения: «30.10.2026», с временем — «30.10.2026 21:00». */
function textOf(iso: string | null): string {
  const [day, time] = split(iso)
  if (!day) return ''
  return props.withTime ? `${day} ${time ?? '00:00'}` : day
}

const placeholder = computed(() => (props.withTime ? t('дд.мм.гггг чч:мм') : t('дд.мм.гггг')))

// текст поля: пока пользователь печатает, — его текст, иначе — значение
const editing = ref(false)
const draftText = ref('')
const typed = computed(() => (editing.value ? draftText.value : textOf(model.value)))

// --- ручной ввод ---

// группы записи: день, месяц, год, часы, минуты — и сколько цифр в каждой самое большее
const GROUP_MAX = [2, 2, 4, 2, 2]
const GROUP_SEP = ['.', '.', ' ', ':']

interface Groups {
  /** цифры каждой набранной группы, без дополнения нулём */
  digits: string[]
  /** группа, которую сейчас набирают; группы до неё закрыты */
  current: number
}

/**
 * Разбор набранного текста по группам. Цифра идёт в текущую группу; группа заполнена — в следующую. Разделитель
 * (любой нецифровой знак) после заполненной группы переводит к следующей. Неполную группу закрывает только
 * разделитель, набранный сейчас (typed — его позиция в тексте): «1.» — день 01, «01.01.20 » — год 2020: год из одной
 * или двух цифр сразу дополняется до 20xx («3 » — 2003). Уже стоявший разделитель после неполной группы пропускается, и цифры сдвигаются,
 * как в обычной маске: стёртая в середине цифра не превращает «02.11» в «02.01». Разделитель после пустой группы
 * ничего не значит. caret — позиция в тексте; возвращается, в какой группе и после скольких её цифр она оказалась.
 */
function parseGroups(
  raw: string,
  caret: number,
  typed: (i: number) => boolean,
): Groups & { caretGroup: number; caretLen: number } {
  const last = props.withTime ? 4 : 2
  const digits = ['']
  let current = 0
  let caretGroup = 0
  let caretLen = 0
  for (let i = 0; i < raw.length; i++) {
    if (i === caret) [caretGroup, caretLen] = [current, digits[current]!.length]
    const ch = raw[i]!
    if (/\d/.test(ch)) {
      if (digits[current]!.length === GROUP_MAX[current]) {
        if (current === last) continue
        digits[++current] = ''
      }
      digits[current] += ch
    } else {
      let len = digits[current]!.length
      if (current === 2 && (len === 1 || len === 2) && typed(i)) {
        digits[2] = `20${digits[2]!.padStart(2, '0')}`
        len = 4
      }
      if (current < last && (len === GROUP_MAX[current] || (len > 0 && typed(i)))) digits[++current] = ''
    }
  }
  if (caret >= raw.length) [caretGroup, caretLen] = [current, digits[current]!.length]
  return { digits, current, caretGroup, caretLen }
}

/** Закрытая группа из одной цифры (кроме года) дополняется нулём: «1.» → «01.». */
function groupText(groups: Groups, i: number): string {
  const d = groups.digits[i]!
  return i < groups.current && i !== 2 && d.length === 1 ? `0${d}` : d
}

/** Группы → текст с разделителями: «02.1», «01.12.26 14:30»; разделитель после закрытой группы виден сразу. */
function formatGroups(groups: Groups): string {
  let text = ''
  for (let i = 0; i <= groups.current; i++) text += (i > 0 ? GROUP_SEP[i - 1] : '') + groupText(groups, i)
  return text
}

/**
 * Ввод в поле: «02.11.2026 00:00» и «021120260000» равноценны — разделители встают сами. Набранная точка закрывает
 * день или месяц из одной цифры («1.2.2026»). Год можно набрать одной или двумя цифрами: «011226» — 01.12.2026 по
 * Enter или уходу из поля, а набранный после года разделитель сразу дописывает «20»: «010120 » — «01.01.2020 »,
 * «01.02.3 » — «01.02.2003 »; со временем короткий год — только так («011226 1430»). Курсор остаётся после той же цифры, что и до разбора, поэтому не прыгает.
 * Дата с годом из четырёх цифр (и временем, если оно есть) применяется сразу; остальное — по Enter или уходу из поля.
 */
function onTypedInput(e: Event) {
  const el = e.target as HTMLInputElement
  const caret = el.selectionStart ?? el.value.length
  // набранный сейчас текст: при наборе — перед курсором; при удалении — ничего; вставка и прочее — весь текст
  const type = (e as InputEvent).inputType ?? ''
  const from = type === 'insertText' ? caret - ((e as InputEvent).data?.length ?? 0) : 0
  const to = type === 'insertText' ? caret : type.startsWith('delete') ? 0 : el.value.length
  const groups = parseGroups(el.value, caret, (i) => i >= from && i < to)
  const text = formatGroups(groups)

  editing.value = true
  draftText.value = text
  el.value = text

  let pos = groups.caretLen
  if (groups.caretGroup <= groups.current) {
    pos += groupText(groups, groups.caretGroup).length - groups.digits[groups.caretGroup]!.length
  }
  for (let i = 0; i < groups.caretGroup && i <= groups.current; i++) pos += groupText(groups, i).length + 1
  el.setSelectionRange(pos, pos)

  const last = props.withTime ? 4 : 2
  if (groups.current === last && groups.digits[last]!.length === GROUP_MAX[last]) applyText(text)
}

/**
 * Разобрать текст поля и применить: «30.10.2026», «1.2.26», «30.10.26 21:00». Год из одной или двух цифр — 20xx. Дата без
 * времени в поле с временем сохраняет прежнее время. Пусто — дата очищается, если она может быть пустой. Ошибка
 * в дате — значение не меняется. Возвращает, удалось ли применить.
 */
function applyText(text: string): boolean {
  if (!text) {
    if (props.nullable) model.value = null
    return props.nullable
  }
  const m = /^(\d{1,2})\.(\d{1,2})\.(\d{1,2}|\d{4})(?: (\d{1,2}):(\d{2}))?$/.exec(text)
  if (!m) return false
  const [, d, mo, y, h, mi] = m
  const pad = (v: string) => v.padStart(2, '0')
  const day = `${pad(d!)}.${pad(mo!)}.${y!.length < 4 ? `20${pad(y!)}` : y}`
  // несуществующая дата (31.02) после разбора даёт другой день — такую не принимаем
  if (date.formatDate(date.extractDate(day, DATE_FORMAT), DATE_FORMAT) !== day) return false
  if (h !== undefined && (Number(h) > 23 || Number(mi) > 59)) return false
  const time = h !== undefined ? `${pad(h)}:${mi}` : (split(model.value)[1] ?? null)
  model.value = join(day, time)
  return true
}

/** Enter или уход из поля: применить введённое; не удалось — поле вернётся к прежнему значению. */
function commitTyped() {
  if (!editing.value) return
  editing.value = false
  applyText(draftText.value.trim())
}

/** Начало ввода: в поле остаётся текущее значение — его правят, а не набирают заново. */
function startTyping() {
  draftText.value = textOf(model.value)
  editing.value = true
}

function cancelTyped() {
  editing.value = false
}

/** Escape в поле: пока идёт ввод — только отменяет его и дальше не уходит (не закрывает диалог или страницу). */
function onEsc(e: KeyboardEvent) {
  if (!editing.value || draftText.value === textOf(model.value)) return
  e.stopPropagation()
  cancelTyped()
}

/** Клик в коротком ряду: быстрый вариант применяется сразу; повторный клик по выбранному при clearable — очистка. */
function onShortClick(value: string) {
  if (value === shortModel.value) {
    if (props.clearable) model.value = null
    return
  }
  const [, time] = split(model.value)
  model.value = join(date.formatDate(quickDateValue(value as QuickDate), DATE_FORMAT), time)
}

// --- окно ---

function open() {
  ;[dateInner.value, timeInner.value] = split(model.value)
  // каждое открытие на мобильном начинается с быстрых вариантов
  calendarsShown.value = false
  popup.value?.show()
}

function close() {
  // окно считается закрытым сразу, а не после анимации: dialogHistory сразу после шага «назад» спрашивает isShown
  popupShown = false
  popup.value?.hide()
}

function showCalendar() {
  calendarsShown.value = true
}

function hideCalendar() {
  calendarsShown.value = false
}

/**
 * Кнопка «назад» на телефоне сворачивает выбор по шагам — от календаря к быстрым вариантам, потом закрывает окно —
 * через общий стек диалогов, как у PeriodPicker: от календаря окно остаётся открытым, и dialogHistory кладёт запись
 * заново.
 */
let popupShown = false
const historyEntry: HistoryDialog = { isShown: () => popupShown, close: stepBack }

function stepBack() {
  if (calendarsShown.value && props.quick.length > 0) {
    hideCalendar()
    return
  }
  close()
}

function onPopupShow() {
  popupShown = true
  if (isFullScreen()) dialogHistory.push(historyEntry)
}

function onPopupHide() {
  popupShown = false
  dialogHistory.pop(historyEntry)
}

onBeforeUnmount(() => dialogHistory.forget(historyEntry))

function onQuickUpdate(code: QuickDate) {
  onDateUpdate(date.formatDate(quickDateValue(code), DATE_FORMAT))
  if (quickPanelApplies.value) apply()
}

function onDateUpdate(value: string | null) {
  dateInner.value = value
  // «Очистить» сбрасывает и время: время без даты не имеет смысла
  if (value === null) timeInner.value = null
  if (value && props.withTime && !timeInner.value) timeInner.value = '00:00'
  if (props.applyImmediately) model.value = join(dateInner.value, timeInner.value)
}

// --- время ---

/** Деление циферблата: значение, подпись, позиция (0–11 по кругу, 0 — вверху) и кольцо. */
interface TimeCell {
  kind: 'hour' | 'minute'
  value: number
  label: string
  position: number
  inner: boolean
}

const pad2 = (n: number) => String(n).padStart(2, '0')

// часы: внешнее кольцо 0–11, внутреннее 12–23; минуты: 00–55 с шагом 5
const TIME_DIALS: { kind: 'hour' | 'minute'; cells: TimeCell[] }[] = [
  {
    kind: 'hour',
    cells: Array.from({ length: 24 }, (_, h) => ({
      kind: 'hour',
      value: h,
      label: String(h),
      position: h % 12,
      inner: h >= 12,
    })),
  },
  {
    kind: 'minute',
    cells: Array.from({ length: 12 }, (_, i) => ({
      kind: 'minute',
      value: i * 5,
      label: pad2(i * 5),
      position: i,
      inner: false,
    })),
  },
]

// радиус колец — доля радиуса циферблата
const OUTER_RADIUS = 0.8
const INNER_RADIUS = 0.52

function radiusOf(cell: TimeCell): number {
  return cell.inner ? INNER_RADIUS : OUTER_RADIUS
}

/** Положение деления на круге: центр циферблата — 50%, 0 — вверху, по часовой стрелке. */
function cellStyle(cell: TimeCell): Record<string, string> {
  const angle = (cell.position * Math.PI) / 6
  const r = radiusOf(cell) * 50
  return { left: `${50 + r * Math.sin(angle)}%`, top: `${50 - r * Math.cos(angle)}%` }
}

/**
 * Стрелка от центра к выбранному значению: у часов — к выбранному делению (с учётом кольца), у минут — к точной
 * минуте (6° на минуту), даже если она не кратна пяти и деления для неё нет.
 */
function hand(kind: 'hour' | 'minute'): Record<string, string> | null {
  if (!timeInner.value) return null
  const [h, m] = timeInner.value.split(':').map(Number)
  if (kind === 'minute') return { height: `${OUTER_RADIUS * 50}%`, transform: `rotate(${(m ?? 0) * 6}deg)` }
  const cell = TIME_DIALS[0]!.cells.find((c) => c.value === h)
  return cell ? { height: `${radiusOf(cell) * 50}%`, transform: `rotate(${cell.position * 30}deg)` } : null
}

/**
 * Деление под указателем: по углу от центра — ближайшая позиция, по расстоянию от центра — кольцо (у часов: ближе к
 * центру — внутреннее, 12–23). Для минут — ближайшая с шагом 5.
 */
function cellAt(e: PointerEvent, cells: TimeCell[]): TimeCell | undefined {
  const rect = (e.currentTarget as HTMLElement).getBoundingClientRect()
  const x = e.clientX - (rect.left + rect.width / 2)
  const y = e.clientY - (rect.top + rect.height / 2)
  const angle = (Math.atan2(x, -y) * 180) / Math.PI
  const position = Math.round((((angle % 360) + 360) % 360) / 30) % 12
  const distance = Math.hypot(x, y) / (rect.width / 2)
  const inner = distance < (OUTER_RADIUS + INNER_RADIUS) / 2
  return cells.find((c) => c.position === position && c.inner === inner) ?? cells.find((c) => c.position === position)
}

// перетаскивание по циферблату: нажали — выбирается деление под указателем, двигают с нажатой кнопкой — оно меняется
let dragging = false

function onDialDown(e: PointerEvent, cells: TimeCell[]) {
  if (dateInner.value === null) return
  dragging = true
  // указатель захвачен: перетаскивание продолжается и за пределами циферблата
  ;(e.currentTarget as HTMLElement).setPointerCapture(e.pointerId)
  pick(e, cells)
}

function onDialMove(e: PointerEvent, cells: TimeCell[]) {
  if (dragging) pick(e, cells)
}

function onDialUp() {
  dragging = false
}

function pick(e: PointerEvent, cells: TimeCell[]) {
  // минуты — с точностью до одной: ближайшая минута по углу
  if (cells[0]?.kind === 'minute') {
    const minute = minuteAt(e)
    const [h] = (timeInner.value ?? '00:00').split(':')
    const value = `${h}:${pad2(minute)}`
    if (value !== timeInner.value) onTimeUpdate(value)
    return
  }
  const cell = cellAt(e, cells)
  if (cell && !isTimeActive(cell)) onTimeCell(cell)
}

/** Минута под указателем: по углу от центра, 6° на минуту. */
function minuteAt(e: PointerEvent): number {
  const rect = (e.currentTarget as HTMLElement).getBoundingClientRect()
  const x = e.clientX - (rect.left + rect.width / 2)
  const y = e.clientY - (rect.top + rect.height / 2)
  const angle = ((((Math.atan2(x, -y) * 180) / Math.PI) % 360) + 360) % 360
  return Math.round(angle / 6) % 60
}

function isTimeActive(cell: TimeCell): boolean {
  if (!timeInner.value) return false
  const [h, m] = timeInner.value.split(':').map(Number)
  return cell.kind === 'hour' ? cell.value === h : cell.value === m
}

/** Час меняет только часы, минута — только минуты; второе берётся из текущего времени (или 00). */
function onTimeCell(cell: TimeCell) {
  const [h, m] = (timeInner.value ?? '00:00').split(':')
  onTimeUpdate(cell.kind === 'hour' ? `${pad2(cell.value)}:${m}` : `${h}:${pad2(cell.value)}`)
}

function onTimeUpdate(value: string | null) {
  timeInner.value = value
  if (props.applyImmediately) model.value = join(dateInner.value, timeInner.value)
}

function apply() {
  model.value = join(dateInner.value, timeInner.value)
  close()
}

// --- шапка календаря, как у PeriodPicker ---

function formatYearHeader(day: string | null): string {
  if (day === null) return ' '
  return String(date.extractDate(day, DATE_FORMAT).getFullYear())
}

/** Заголовок общей шапки: «1 ноября, воскресенье», со временем — «1 ноября, воскресенье, 23:09». */
const headerTitle = computed(() => {
  const title = formatDateHeader(dateInner.value)
  return props.withTime && dateInner.value && timeInner.value ? `${title}, ${timeInner.value}` : title
})

function formatDateHeader(day: string | null): string {
  if (day === null) return t('не выбрана')
  const formatter = new Intl.DateTimeFormat(i18n.global.locale.value, {
    day: 'numeric',
    month: 'long',
    weekday: 'long',
  })
  const parts = formatter.formatToParts(date.extractDate(day, DATE_FORMAT))
  const part = (type: string) => parts.find((p) => p.type === type)?.value
  return `${part('day')} ${part('month')}, ${part('weekday')}`
}
</script>

<style scoped lang="scss">
// Рамка-пилюля как у PeriodPicker: кнопки и «...» — во всю внутреннюю высоту рамки, со скруглением рамки
.date-picker {
  --date-picker-border: 0.0625rem;
  --date-picker-radius: calc(var(--zs-control-height) / 2);

  display: inline-flex;
  flex-wrap: nowrap;
  align-items: stretch;
  gap: 0.125rem;
  // рамка растёт по содержимому: выбранная дата никогда не обрезается
  width: max-content;
  max-width: none;
  height: var(--zs-control-height);
  border: var(--date-picker-border) solid $zs-border-input;
  border-radius: var(--date-picker-radius);
  background: #fff;
}

.date-picker__label {
  display: flex;
  align-items: center;
  margin: 0 0.375rem 0 0.75rem;
  color: $zs-text-muted;
  font-size: 0.8125rem;
  white-space: nowrap;
}

// Каждая кнопка короткого ряда — свой сегмент-пилюля
.date-picker__short {
  display: flex;
  height: 100%;
  gap: 0.125rem;

  :deep(.q-btn) {
    min-height: 0;
    height: 100%;
    padding: 0 0.75rem;
    border-radius: calc(var(--date-picker-radius) - var(--date-picker-border));
    font-size: 0.8125rem;
    font-weight: 400;
    font-variant-numeric: tabular-nums;
    white-space: nowrap;
  }

  :deep(.date-picker__btn) {
    color: $zs-text;
  }
}

// фокус в поле даты — рамка подсвечивается, как у остальных полей
.date-picker:focus-within {
  border-color: $primary;
}

// поле даты: ширина постоянная, в em — 5.8em для даты, 8.8em для даты со временем; при наборе не меняется.
// Слева отступ больше, чем справа: справа у кнопок быстрых вариантов свой внутренний отступ
.date-picker__input {
  flex: none;
  box-sizing: content-box;
  width: 5.8em;
  padding: 0 0.5rem 0 1rem;
  border: 0;
  background: transparent;
  color: $zs-text;
  font: inherit;
  font-size: 0.8125rem;
  // обычный шрифт явно: подпись поля формы (FormField) задаёт полужирный, и поле его наследовало бы
  font-weight: 400;
  font-variant-numeric: tabular-nums;
  outline: none;

  &::placeholder {
    color: $zs-text-faint;
  }

  &--time {
    width: 8.8em;
  }
}

.date-picker__more {
  min-width: 0;
  min-height: 0;
  height: 100%;
  margin-left: auto;
  padding: 0 0.375rem;
  border-radius: calc(var(--date-picker-radius) - var(--date-picker-border));
  color: $zs-text-muted;
}

.date-picker__quick :deep(.q-btn) {
  color: $zs-text;
}

.q-btn-toggle.column .q-btn:first-child {
  border-radius: 0.5rem 0.5rem 0 0;
}

.q-btn-toggle.column .q-btn:last-child {
  border-radius: 0 0 0.5rem 0.5rem;
}

.q-btn-toggle.column .q-btn:not(:first-child):not(:last-child) {
  border-radius: 0;
}

.quick-panel-compact {
  min-width: 15rem;
}

// календарь со временем и общей шапкой; снизу — место под плавающую кнопку «Ок» (высота обычной кнопки)
.date-picker__calendar {
  display: flex;
  flex-direction: column;

  &--bottom {
    padding-bottom: 2.3rem;
  }
}

// общая шапка календаря и времени — в цветах шапки календаря Quasar
.date-picker__header {
  padding: 0.5rem 0.5rem 1rem 1rem;
  border-radius: 0.25rem 0.25rem 0 0;
  background: $zs-bg-header;
}

.date-picker__header-text {
  color: $zs-text;
  font-size: 0.9375rem;
  font-weight: 600;
}

.date-picker__header-year {
  margin-top: 0.25rem;
  color: $primary;
  font-size: 0.875rem;
}

.date-picker__header-title {
  color: $primary;
  font-size: 1.2rem;

  .date-picker__header--empty & {
    color: $zs-text-faint;
  }
}

.date-picker__panels {
  gap: 1rem;
}

// Время — в цветах календаря (q-date): шапка той же высоты и того же фона, выбранное — синей заливкой
.time-grid {
  display: flex;
  flex-direction: column;
  min-width: 9rem;

  &--disabled {
    opacity: 0.5;
  }
}

// циферблаты рядом: часы, справа минуты; на узком экране, где рядом не помещаются, — друг под другом
.time-grid__body {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  align-items: center;
  gap: 1rem;
  padding: 1rem;
}

.time-dial {
  position: relative;
  // перетаскивание пальцем не прокручивает страницу
  touch-action: none;
  user-select: none;
  cursor: pointer;
  width: 10rem;
  height: 10rem;
  border-radius: 50%;
  background: $zs-bg-header;
}

.time-dial__center {
  position: absolute;
  top: 50%;
  left: 50%;
  width: 0.3rem;
  height: 0.3rem;
  border-radius: 50%;
  background: $primary;
  transform: translate(-50%, -50%);
}

// стрелка растёт от центра вверх и поворачивается к выбранному делению
.time-dial__hand {
  position: absolute;
  bottom: 50%;
  left: calc(50% - 0.05rem);
  width: 0.1rem;
  background: $primary;
  transform-origin: bottom center;

  // конец стрелки — точка: видна, когда минута не кратна пяти и подсвеченного деления нет
  &::after {
    content: '';
    position: absolute;
    top: -0.2rem;
    left: -0.2rem;
    width: 0.5rem;
    height: 0.5rem;
    border-radius: 50%;
    background: $primary;
  }
}

.time-dial__cell {
  position: absolute;
  width: 1.8rem;
  height: 1.8rem;
  padding: 0;
  border: 0;
  border-radius: 50%;
  background: transparent;
  color: $zs-text;
  font: inherit;
  font-size: 0.875rem;
  font-variant-numeric: tabular-nums;
  line-height: 1;
  transform: translate(-50%, -50%);
  cursor: pointer;

  &--inner {
    color: $zs-text-muted;
    font-size: 0.75rem;
  }

  &:hover:not(:disabled):not(.time-dial__cell--active) {
    background: rgba($primary, 0.1);
  }

  &--active {
    background: $primary;
    color: #fff;
  }

  &:disabled {
    cursor: default;
  }
}

.custom-date-mobile {
  width: auto;
  min-width: 18rem;
}

.period-back {
  position: absolute;
  top: 0;
  left: 0;
  z-index: 10;
}

.period-apply {
  position: absolute;
  right: 0;
  bottom: 0;
  z-index: 10;
  white-space: nowrap;
}

.period-card-padded .period-apply {
  right: 1rem;
  bottom: 1rem;
}
</style>
