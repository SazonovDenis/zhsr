<template>
  <!-- Единый корень — без него группа кнопок и кнопка «...» попадают в DOM родителя как два независимых узла, и любой
       внешний flex/grid растягивает и позиционирует их порознь -->
  <div class="period-picker">
    <span v-if="label" class="period-picker__label">{{ t(label) }}</span>

    <!-- поля ввода нет — ряд как переключатель: одна остановка Tab, внутри стрелки, пробел и Backspace
         (v-roving-buttons); «...» Tab пропускает -->
    <q-btn-toggle
      v-roving-buttons
      :model-value="periodShort"
      class="period-picker__short"
      toggle-color="selected"
      toggle-text-color="selected"
      no-caps
      unelevated
      :clearable="clearable"
      :options="periodOptionsShort"
      @update:model-value="onPeriodUpdateShort"
    />

    <q-btn flat dense icon="more_horiz" class="period-picker__more" :tabindex="-1" @click="onClick">
      <!-- anchor/self по центру: при нехватке места справа Quasar не обрезает окно по ширине, оставшейся от кнопки
           (с горизонтальным скроллом внутри), а целиком сдвигает его, чтобы вписать в экран -->
      <q-popup-proxy
        ref="popup"
        class="period-picker-popup"
        :breakpoint="SCALE0_MAX_PX"
        anchor="bottom middle"
        self="top middle"
        @show="onPopupShow"
        @hide="onPopupHide"
      >
        <q-card :class="cardClass">
          <div class="row no-wrap" :class="panelsGutterClass">
            <!-- Быстрый выбор -->
            <div v-if="quickPanelShown" class="q-mb-none" :class="quickPanelClass" style="white-space: nowrap">
              <!-- Телефон: закрыть выпадающий блок -->
              <div v-if="closeButtonShown" class="row justify-end q-mb-sm">
                <q-btn dense flat icon="close" color="grey-7" @click="close" />
              </div>

              <!-- Мобильный: выбор готовых вариантов -->
              <q-btn-toggle
                :model-value="period"
                :options="periodOptions"
                :class="quickToggleClass"
                class="period-picker__quick"
                toggle-color="selected"
                toggle-text-color="selected"
                spread
                no-caps
                unelevated
                @update:model-value="(e: PeriodCode) => onPeriodUpdate(e, true)"
              />

              <!-- Мобильный: переход к календарям -->
              <q-separator v-if="isCompact()" class="full-width q-my-md" />

              <q-btn
                v-if="isCompact()"
                flat
                no-caps
                class="full-width"
                :label="t('Указать период...')"
                @click="showCalendars"
              />
            </div>

            <div v-if="calendarsPanelShown" class="row no-wrap" :class="calendarsRowClass" style="width: 100%">
              <!-- Мобильный: возврат от календарей к готовым периодам -->
              <div v-if="backButtonShown" class="period-back">
                <q-icon
                  class="q-px-xs q-pt-sm"
                  name="arrow_back"
                  size="md"
                  color="grey-7"
                  @click.stop="hideCalendars"
                />
              </div>

              <div class="row period-picker__calendars" :class="calendarsPanelClass">
                <!-- Дата начала -->
                <div :class="[dateColClass, startColClass]">
                  <q-date
                    class="custom-date relative-position"
                    :class="[dateMobileClass, emptyDateClass(startDateInner), 'custom-date--start']"
                    :model-value="startDateInner"
                    :mask="DATE_FORMAT"
                    :title="formatDateHeader(startDateInner)"
                    :subtitle="formatYearHeader(startDateInner)"
                    :no-unset="!nullableStartDate"
                    flat
                    @update:model-value="(e: string | null) => onStartUpdate(e, true)"
                  >
                    <!-- Слот рисуется в .q-date__actions (внизу), поэтому строку с подписью и кнопкой «Очистить»
                         вытягиваем абсолютом в шапку — место под неё зарезервировано отступом .q-date__header -->
                    <div class="date-header-line row items-center justify-between no-wrap">
                      <div class="date-header-line__text">{{ startDateTextResolved }}</div>
                      <q-btn
                        v-if="nullableStartDate && startDateInner"
                        dense
                        flat
                        no-caps
                        icon-right="close"
                        color="grey-6"
                        :label="t('Очистить')"
                        @click="onStartUpdate(null, true)"
                      />
                    </div>
                  </q-date>
                </div>

                <!-- Дата конца -->
                <div :class="dateColClass">
                  <q-date
                    class="custom-date relative-position"
                    :class="[dateMobileClass, emptyDateClass(endDateInner)]"
                    :model-value="endDateInner"
                    :mask="DATE_FORMAT"
                    :title="formatDateHeader(endDateInner)"
                    :subtitle="formatYearHeader(endDateInner)"
                    :no-unset="!nullableEndDate"
                    flat
                    @update:model-value="(e: string | null) => onEndUpdate(e, true)"
                  >
                    <div class="date-header-line row items-center justify-between no-wrap">
                      <div class="date-header-line__text">{{ endDateTextResolved }}</div>
                      <q-btn
                        v-if="nullableEndDate && endDateInner"
                        dense
                        flat
                        no-caps
                        icon-right="close"
                        color="grey-6"
                        :label="t('Очистить')"
                        @click="onEndUpdate(null, true)"
                      />
                    </div>
                  </q-date>
                </div>
              </div>

              <!-- Планшет: возврат к готовым периодам. Стрелки сверху там нет, поэтому кнопкой в углу, напротив
                   кнопки применения -->
              <div v-if="listButtonShown" class="period-list">
                <q-btn no-caps outline color="grey-8" :label="t('Выбрать из списка')" @click="hideCalendars" />
              </div>

              <!-- Применение периода -->
              <div v-if="okButtonShown" class="period-apply">
                <q-btn
                  no-caps
                  unelevated
                  color="primary"
                  :disable="applyDisabled"
                  :label="okButtonTextResolved"
                  @click="apply"
                />
              </div>
            </div>
          </div>

          <div class="row items-center justify-center period-picker__error">{{ periodError?.text }}</div>
        </q-card>
      </q-popup-proxy>
    </q-btn>
  </div>
</template>

<script setup lang="ts">
import type { QPopupProxy } from 'quasar'
import { date } from 'quasar'
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'

import type { HistoryDialog } from '@/shared/components/dialog/dialogHistory'
import { dialogHistory } from '@/shared/components/dialog/dialogHistory'
import { vRovingButtons } from '@/shared/composables/rovingButtons'

import { i18n, t } from '@/boot/i18n'
import { isLandscape, SCALE0_MAX_PX, screenScale, screenScaleMin } from '@/shared/screen'

/**
 * Выбор периода: короткий ряд готовых периодов («Сегодня», «3 дня», «Неделя») и «...» с полным списком готовых
 * периодов и двумя календарями.
 *
 *   <PeriodPicker v-model:start-date="from" v-model:end-date="to" label="Период:" />
 *
 * - Даты снаружи — ISO-даты «2026-10-30» (startDate/endDate, события update:startDate/update:endDate); внутри
 *   компонент работает с DD.MM.YYYY (mask у q-date и арифметика периодов). Конвертация только на границе.
 * - Готовый период выводится из самих дат (getPeriodCode): диапазон может прийти снаружи, и подсвечена должна быть
 *   кнопка, которой он отвечает. Если период не из коротких, третья кнопка короткого ряда показывает его подписью.
 * - Пустая дата — это пусто (null), а не сегодня.
 * - Выбор в коротком ряду применяется сразу. В окне «...» период применяется кнопкой «Ок» (или сразу при
 *   applyImmediately).
 * - Раскладка окна зависит от экрана: обычная (готовые периоды и календари рядом), компактная (одна панель с
 *   переходом от готовых периодов к календарям) и полноэкранная на телефоне (свои кнопки закрытия и возврата,
 *   кнопка «назад» браузера сворачивает выбор по шагам).
 */

type PeriodCode = 'd1' | 'd3' | 'd7' | 'm1' | 'mStart' | 'm3' | 'm6' | 'y' | 'yStart' | 'custom'
interface PeriodError {
  text: string
}

const DATE_FORMAT = 'DD.MM.YYYY'
const ISO_DATE = 'YYYY-MM-DD'

/** DD.MM.YYYY → ISO-дата. Дата без времени и без часового пояса: toISOString сдвинул бы её на сутки назад. */
function toIso(dmyStr: string | null): string | null {
  if (!dmyStr) return null
  return date.formatDate(date.extractDate(dmyStr, DATE_FORMAT), ISO_DATE)
}

/** ISO-дата (или ISO с временем — берётся дата) → DD.MM.YYYY. */
function fromIso(isoStr: string | null | undefined): string | null {
  if (!isoStr) return null
  return date.formatDate(date.extractDate(isoStr.slice(0, 10), ISO_DATE), DATE_FORMAT)
}

function today(): string {
  return date.formatDate(new Date(), DATE_FORMAT)
}

const props = withDefaults(
  defineProps<{
    /** проверка периода: принимает начало и конец (ISO-даты или null) и возвращает ошибку или null */
    periodCheck?: ((start: string | null, end: string | null) => PeriodError | null) | null
    applyImmediately?: boolean
    /** тексты на русском; переводятся при показе */
    okButtonText?: string
    startDateText?: string
    endDateText?: string
    /** ISO-дата «2026-10-30» */
    startDate?: string | null
    endDate?: string | null
    nullableStartDate?: boolean
    nullableEndDate?: boolean
    /** повторный клик по уже выбранной кнопке короткого периода очищает значение */
    clearable?: boolean
    /** подпись внутри рамки перед кнопками, например «Период:» — на русском */
    label?: string
  }>(),
  {
    periodCheck: null,
    applyImmediately: false,
    okButtonText: '',
    startDateText: '',
    endDateText: '',
    startDate: null,
    endDate: null,
    nullableStartDate: true,
    nullableEndDate: true,
    clearable: false,
    label: '',
  },
)

const emit = defineEmits<{
  'update:startDate': [value: string | null]
  'update:endDate': [value: string | null]
}>()

const popup = ref<QPopupProxy | null>(null)

// Период выводим из самих дат (см. getPeriodCode), а не подставляем наугад: диапазон приходит и снаружи — из готового
// фильтра, из восстановленных условий, — и подсвечена должна быть та кнопка, которой этот диапазон отвечает
const initialPeriod = getPeriodCode(fromIso(props.startDate), fromIso(props.endDate))

const period = ref<PeriodCode | null>(initialPeriod)
const periodShort = ref<PeriodCode | null>(initialPeriod)
// пусто — это пусто: незаданная дата не подменяется сегодняшней
const startDateInner = ref<string | null>(fromIso(props.startDate))
const endDateInner = ref<string | null>(fromIso(props.endDate))
const periodError = ref<PeriodError | null>(null)
const applyDisabled = ref(false)

// Только для мобильного: какая из двух панелей сейчас видна
const calendarsShown = ref(false)

// --- раскладка ---

/**
 * Компактная раскладка — в одну панель, с переходом от готовых периодов к календарям. Выбирается по реальной ширине
 * экрана, а не по типу устройства: платформа о доступном месте не говорит ничего (Quasar считает мобильным любой
 * Android-планшет и iPad, флага планшета у него нет), а решает здесь только оно. Обычной раскладке нужно около 830px:
 * панель готовых периодов, отступ, два календаря и поля карточки. Первая корзина screenScale, которая это
 * гарантирует, — третья, от 950px.
 */
function isCompact(): boolean {
  return screenScale() < 3
}

/**
 * Попап раскрыт во весь экран. Второй режим компонента, независимый от компактности: он про то, остаётся ли вокруг
 * попапа экран, а не про то, сколько панелей помещается внутри. От него зависят поля карточки, крестик закрытия,
 * стрелка возврата и кнопка «Выбрать из списка» — когда попап накрывает экран целиком, щёлкнуть мимо него нельзя, и
 * закрывать с возвратом приходится своими кнопками.
 * Мера — масштаб по меньшей стороне, а не по ширине: телефон лёжа это 844x390, места по горизонтали вдоволь, а попап
 * всё равно занимает экран целиком. Нулевая корзина этой меры отдана в breakpoint попапа (SCALE0_MAX_PX), поэтому
 * оформление и то, что реально нарисовано, разойтись не могут.
 */
function isFullScreen(): boolean {
  return screenScaleMin() === 0
}

const okButtonTextResolved = computed(() => t(props.okButtonText || 'Ок'))

// Выбор на панели готовых периодов применяется сразу и закрывает контрол — кнопка «Ок» там не нужна. Панель отдельно
// существует только на мобильном, на десктопе она идёт вместе с календарями и подчиняется общей кнопке
const quickPanelApplies = computed(() => isCompact() && !calendarsShown.value)

const okButtonShown = computed(() => !quickPanelApplies.value && !props.applyImmediately)

// На мобильном экране помещается только одна панель — показываем либо готовые периоды, либо календари
const quickPanelShown = computed(() => !isCompact() || !calendarsShown.value)
const calendarsPanelShown = computed(() => !isCompact() || calendarsShown.value)

// Возврат к готовым периодам нужен только на телефоне: там попап раскрыт во весь экран и мимо него не щёлкнуть.
// Начиная с планшета это меню — оно закрывается щелчком снаружи, а открывается всегда с панели периодов (см. onClick)
const backButtonShown = computed(() => isFullScreen() && calendarsShown.value)

// Начиная с планшета место возврата занимает кнопка внизу: полноэкранного попапа там нет, и стрелка в углу над
// календарём выглядела бы случайной
const listButtonShown = computed(() => isCompact() && !isFullScreen() && calendarsShown.value)

// Крестик нужен только на телефоне: попап во весь экран, мимо него не щёлкнуть
const closeButtonShown = computed(() => isFullScreen())

// q-gutter-md вешает на контейнер отрицательный margin, а на детей — положительный: вместе с растянутой панелью это
// дало бы вылет за правый край и горизонтальный скролл. На мобильном панель всё равно одна, разделять нечего
const panelsGutterClass = computed(() => (isCompact() ? '' : 'q-gutter-md'))

// В горизонтальном режиме список готовых периодов раскладываем в две колонки
const quickToggleClass = computed(() => (isCompact() && isLandscape() ? 'quick-toggle-2col' : 'column'))

const quickPanelClass = computed(() => (isCompact() ? 'full-width quick-panel-compact' : ''))

// Стоят ли календари в строку. В обычной раскладке они и так в строке, там речь только о компактной
const calendarsSideBySide = computed(() => isCompact() && (isLandscape() || !isFullScreen()))

// col отдаёт панели всё место, оставшееся в строке от боковых блоков; justify-center — чтобы календари не липли к
// левому краю. Рядом (no-wrap) они встают, когда по ширине помещаются: столько есть на любом экране крупнее
// полноэкранного режима, а внутри него — только лёжа, где ширины хватает, а высоты на два календаря столбиком уже нет
const calendarsPanelClass = computed(() => {
  if (!isCompact()) return ''
  return calendarsSideBySide.value ? 'col justify-center no-wrap' : 'col justify-center'
})

// Календари, стоящие рядом, не должны слипаться. Вне телефона зазор шире: там и экран просторнее, и карточка с полями
const startColClass = computed(() => {
  if (!calendarsSideBySide.value) return ''
  return isFullScreen() ? 'date-col-gap' : 'date-col-gap date-col-gap-wide'
})

// Поля не нужны только на телефоне: там карточка раскрыта во весь экран и сама служит фоном. relative-position —
// точка отсчёта для плавающих кнопок (см. .period-apply и соседей)
const cardClass = computed(() =>
  isFullScreen() ? 'relative-position' : 'q-pa-md relative-position period-card-padded',
)

// Все кнопки плавают поверх содержимого (см. .period-back, .period-apply, .period-list) и его не сдвигают. Место
// резервируем в одном случае: в меню карточка обжимает содержимое, и нижние кнопки ложатся прямо на последнюю строку
// сетки. В полноэкранном режиме они прижаты к краю экрана, до которого содержимое не достаёт
const calendarsRowClass = computed(() => {
  if (isFullScreen()) return ''
  return okButtonShown.value || listButtonShown.value ? 'period-calendars-bottom' : ''
})

// Модификатор для правил шапки календаря, которые нужны только на мобильном
const dateMobileClass = computed(() => (isCompact() ? 'custom-date-mobile' : ''))

// col держит календари строго в ряд — на мобильном он не нужен, тогда они переносятся
const dateColClass = computed(() => (isCompact() ? 'date-col-mobile' : 'col'))

const startDateTextResolved = computed(() => t(props.startDateText || 'Начальная дата'))
const endDateTextResolved = computed(() => t(props.endDateText || 'Конечная дата'))

// --- готовые периоды ---

const periodOptions = computed<{ label: string; value: PeriodCode }[]>(() => [
  { label: t('Сегодня'), value: 'd1' },
  { label: t('3 дня'), value: 'd3' },
  { label: t('Неделя'), value: 'd7' },
  { label: t('Месяц'), value: 'm1' },
  { label: t('С начала месяца'), value: 'mStart' },
  { label: t('3 месяца'), value: 'm3' },
  { label: t('Полгода'), value: 'm6' },
  { label: t('Год'), value: 'y' },
  { label: t('С начала года'), value: 'yStart' },
])

// Короткий ряд — первые три периода; выбранный период не из них показывается подписью на месте третьей кнопки
const periodOptionsShort = computed(() => {
  const options = periodOptions.value.slice(0, 3)
  if (periodShort.value && !options.some((p) => p.value === periodShort.value)) {
    options[2] = { label: dateRangeStr.value ?? '', value: periodShort.value }
  }
  return options
})

const periodLabel = computed(() => periodOptions.value.find((p) => p.value === periodShort.value)?.label ?? null)

const dateRangeStr = computed(() => {
  if (periodShort.value !== 'custom') return periodLabel.value
  return getMonthLabel(fromIso(props.startDate), fromIso(props.endDate)) ?? getDateRangeStr()
})

// --- даты снаружи ---

/**
 * Даты сменились снаружи (применили готовый фильтр, сбросили условия) — пересобираем внутреннее состояние и заново
 * выводим период. Своих правок это не касается: там даты приходят из самого контрола, и период уже посчитан (см.
 * onStartUpdate/onEndUpdate).
 */
watch(() => [props.startDate, props.endDate], applyDatesFromProps)

/** Принять даты, пришедшие снаружи: внутренние значения и период, им отвечающий. */
function applyDatesFromProps() {
  const start = fromIso(props.startDate)
  const end = fromIso(props.endDate)

  startDateInner.value = start
  endDateInner.value = end

  period.value = getPeriodCode(start, end)
  periodShort.value = period.value
}

// --- окно ---

function close() {
  // окно считается закрытым сразу, а не после анимации: dialogHistory сразу после шага «назад» спрашивает, закрылось
  // ли оно (isShown), и иначе вернул бы запись в историю
  popupShown = false
  popup.value?.hide()
}

function showCalendars() {
  calendarsShown.value = true
  updatePopupPosition()
}

function hideCalendars() {
  calendarsShown.value = false
  updatePopupPosition()
}

/**
 * Кнопка «назад» на телефоне должна не уводить со страницы, а сворачивать выбор периода: сначала от календарей к
 * списку готовых периодов, потом закрывать попап.
 *
 * Запись в истории ведёт общий стек диалогов dialogHistory: если выбор периода открыт поверх диалога, «назад»
 * получает только верхнее окно, а не оба сразу. Шаг «назад» вызывает stepBack: от календарей он возвращает к
 * готовым периодам, и окно остаётся открытым — тогда dialogHistory кладёт запись заново, и следующее «назад»
 * закроет окно.
 * Запись заводится только в полноэкранном режиме: в меню на планшете и десктопе кнопка «назад» должна вести себя как
 * обычно.
 */
let popupShown = false
const historyEntry: HistoryDialog = { isShown: () => popupShown, close: stepBack }

function stepBack() {
  if (calendarsShown.value) {
    hideCalendars()
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

/**
 * Пересчитать положение попапа после подмены панели. Меню Quasar позиционируется один раз, по размеру содержимого на
 * момент открытия: если оно встало впритык к краю экрана на узкой панели периодов, более широкие календари уедут за
 * край и обрежутся (у q-menu overflow-x: hidden). У диалога позиция от содержимого не зависит, там пересчёт не нужен.
 */
function updatePopupPosition() {
  const current = (
    popup.value as unknown as { currentComponent?: { type: string; ref?: { updatePosition?: () => void } } }
  )?.currentComponent
  if (current?.type !== 'menu') return
  void nextTick(() => current.ref?.updatePosition?.())
}

// --- проверка и применение ---

// ISO-даты сравниваются строками: порядок строк совпадает с порядком дат
function defaultPeriodCheck(start: string | null, end: string | null): PeriodError | null {
  if (start && end && start > end) return { text: t('Начальная дата не должна быть больше чем конечная') }
  return null
}

// проверке отдаются ISO-даты, как и наружу: внутренний формат DD.MM.YYYY за пределы компонента не выходит
function check() {
  const checkFn = props.periodCheck ?? defaultPeriodCheck
  const error = checkFn(toIso(startDateInner.value), toIso(endDateInner.value))
  periodError.value = error
  applyDisabled.value = !!error
}

function apply() {
  if (applyDisabled.value) return
  emit('update:startDate', toIso(startDateInner.value))
  emit('update:endDate', toIso(endDateInner.value))
  popup.value?.hide()
  periodShort.value = period.value
}

function onPeriodUpdate(code: PeriodCode | null, updateDates = false) {
  period.value = code
  if (updateDates) {
    endDateInner.value = today()
    setDatesByPeriod(code)
    if (props.applyImmediately || quickPanelApplies.value) apply()
  }
}

function onPeriodUpdateShort(code: PeriodCode | null) {
  periodShort.value = code

  // clearable: повторный клик по активной кнопке пришёл как null — очищаем период целиком
  if (code === null) {
    period.value = null
    startDateInner.value = null
    endDateInner.value = null
    check()
    apply()
    return
  }

  onPeriodUpdate(code, true)
  apply()
}

function onStartUpdate(value: string | null, updatePeriod = false) {
  startDateInner.value = value
  check()
  if (updatePeriod) onPeriodUpdate(getPeriodCode(value, endDateInner.value))
  if (props.applyImmediately && !applyDisabled.value) {
    emit('update:startDate', toIso(value))
    periodShort.value = period.value
  }
}

function onEndUpdate(value: string | null, updatePeriod = false) {
  endDateInner.value = value
  check()
  if (updatePeriod) onPeriodUpdate(getPeriodCode(startDateInner.value, value))
  if (props.applyImmediately && !applyDisabled.value) {
    emit('update:endDate', toIso(value))
    periodShort.value = period.value
  }
}

function onClick() {
  startDateInner.value = fromIso(props.startDate)
  endDateInner.value = fromIso(props.endDate)
  period.value = periodShort.value
  // Каждое открытие на мобильном начинается с готовых периодов
  calendarsShown.value = false
}

// --- арифметика периодов ---

function setDatesByPeriod(code: PeriodCode | null) {
  const end = date.extractDate(endDateInner.value ?? today(), DATE_FORMAT)
  let start: Date
  switch (code) {
    case 'd1': // Сегодня
      start = end
      break
    case 'd3': // 3 дня
      start = date.addToDate(end, { days: -2 })
      break
    case 'd7': // Неделя
      start = date.addToDate(end, { days: -6 })
      break
    case 'm1': {
      // Месяц
      const lastDayOfMonth = new Date(end.getFullYear(), end.getMonth() + 1, 0).getDate()
      start = date.addToDate(end, { months: -1 })
      if (end.getDate() === lastDayOfMonth || end.getMonth() === start.getMonth()) {
        start = new Date(end.getFullYear(), end.getMonth(), 1)
      }
      break
    }
    case 'mStart': // С начала месяца
      start = new Date(end.getFullYear(), end.getMonth(), 1)
      break
    case 'm3': // 3 месяца
      start = date.addToDate(end, { months: -3 })
      break
    case 'm6': // Полгода
      start = date.addToDate(end, { months: -6 })
      break
    case 'y': // Год
      start = date.addToDate(end, { years: -1 })
      break
    case 'yStart': // С начала года
      start = new Date(end.getFullYear(), 0, 1)
      break
    default:
      return
  }
  onStartUpdate(date.formatDate(start, DATE_FORMAT))
}

function getPeriodCode(startDate: string | null, endDate: string | null): PeriodCode | null {
  if (!startDate && !endDate) return null
  if (!startDate || !endDate) return 'custom'
  const now = new Date()
  // обнулим время
  const todayDate = new Date(now.getFullYear(), now.getMonth(), now.getDate())
  const start = date.extractDate(startDate, DATE_FORMAT)
  const end = date.extractDate(endDate, DATE_FORMAT)

  // если конец не совпадает с сегодня → не попадает в стандартные коды
  if (end.getTime() !== todayDate.getTime()) return 'custom'

  const totalDaysDiff = Math.round((end.getTime() - start.getTime()) / (1000 * 60 * 60 * 24))
  const monthDaysDiff = end.getDate() - start.getDate()

  if (totalDaysDiff === 0) return 'd1'
  if (totalDaysDiff === 2) return 'd3'
  if (totalDaysDiff === 6) return 'd7'
  if (totalDaysDiff >= 27 && totalDaysDiff <= 31 && monthDaysDiff === 0) return 'm1'
  if (start.getTime() === new Date(end.getFullYear(), end.getMonth(), 1).getTime()) return 'mStart'
  if (totalDaysDiff >= 89 && totalDaysDiff <= 92 && monthDaysDiff === 0) return 'm3'
  if (totalDaysDiff >= 179 && totalDaysDiff <= 184 && monthDaysDiff === 0) return 'm6'
  if (totalDaysDiff >= 364 && totalDaysDiff <= 366 && monthDaysDiff === 0) return 'y'
  if (start.getTime() === new Date(end.getFullYear(), 0, 1).getTime()) return 'yStart'
  return 'custom'
}

const MONTHS = [
  'Январь',
  'Февраль',
  'Март',
  'Апрель',
  'Май',
  'Июнь',
  'Июль',
  'Август',
  'Сентябрь',
  'Октябрь',
  'Ноябрь',
  'Декабрь',
]
const MONTHS_SHORT = ['янв', 'фев', 'мар', 'апр', 'мая', 'июн', 'июл', 'авг', 'сен', 'окт', 'ноя', 'дек']

/** «Октябрь 2026», если период — ровно календарный месяц. */
function getMonthLabel(startDate: string | null, endDate: string | null): string | null {
  if (!startDate || !endDate) return null
  const start = date.extractDate(startDate, DATE_FORMAT)
  const end = date.extractDate(endDate, DATE_FORMAT)

  const year = start.getFullYear()
  const month = start.getMonth()
  const monthStart = new Date(year, month, 1)
  const monthEnd = new Date(year, month + 1, 0)

  // проверка, что диапазон совпадает с месяцем
  if (start.getTime() === monthStart.getTime() && end.getTime() === monthEnd.getTime()) {
    return `${t(MONTHS[month]!)} ${year}`
  }
  return null
}

/** «с 1 по 15 окт 2026», «с 28 сен по 3 окт 2026», «с 5 окт 2026», «по 5 окт 2026». */
function getDateRangeStr(): string {
  const [d1, m1, y1] = fromIso(props.startDate)?.split('.') ?? []
  const [d2, m2, y2] = fromIso(props.endDate)?.split('.') ?? []
  const mon = (m: string | undefined) => t(MONTHS_SHORT[Number(m) - 1] ?? '')

  if (!d1 && !d2) return t('Не выбран')
  if (!d1) return t('по {d} {m} {y}', { d: d2, m: mon(m2), y: y2 })
  if (!d2) return t('с {d} {m} {y}', { d: d1, m: mon(m1), y: y1 })

  if (y1 === y2) {
    if (m1 === m2) return t('с {d1} по {d2} {m} {y}', { d1, d2, m: mon(m1), y: y1 })
    return t('с {d1} {m1} по {d2} {m2} {y}', { d1, m1: mon(m1), d2, m2: mon(m2), y: y1 })
  }
  return t('с {d1} {m1} {y1} по {d2} {m2} {y2}', { d1, m1: mon(m1), y1, d2, m2: mon(m2), y2 })
}

// --- шапка календаря ---

/**
 * Год для шапки календаря. Своё значение нужно только ради пустой даты: Quasar подставил бы туда прочерк, а рядом с
 * надписью «не выбрана» он лишний. Пустую строку проп не принимает (QDate.js, headerSubtitle — проверка на length),
 * поэтому отдаём пробел.
 */
function formatYearHeader(dateStr: string | null): string {
  if (dateStr === null) return ' '
  return String(date.extractDate(dateStr, DATE_FORMAT).getFullYear())
}

/** Модификатор для шапки календаря с невыбранной датой: там стоит заглушка, а не значение. */
function emptyDateClass(dateStr: string | null): string {
  return dateStr === null ? 'date-empty' : ''
}

function formatDateHeader(dateStr: string | null): string {
  if (dateStr === null) return t('не выбрана')
  const currDate = date.extractDate(dateStr, DATE_FORMAT)
  const formatter = new Intl.DateTimeFormat(i18n.global.locale.value, {
    day: 'numeric',
    month: 'long',
    weekday: 'long',
  })
  const parts = formatter.formatToParts(currDate)
  const day = parts.find((p) => p.type === 'day')?.value
  const month = parts.find((p) => p.type === 'month')?.value
  const weekday = parts.find((p) => p.type === 'weekday')?.value
  return `${day} ${month}, ${weekday}`
}
</script>

<style scoped lang="scss">
// Короткий ряд — в общей рамке-пилюле, как CbSegment: подпись, готовые периоды и «...»
// Кнопки периодов и «...» — во всю внутреннюю высоту рамки, со скруглением рамки: выбранная кнопка вписывается в
// рамку ровно, без зазоров. Внутренний радиус кнопки — радиус рамки минус её толщина
.period-picker {
  --period-picker-border: 0.0625rem;
  --period-picker-radius: calc(var(--zs-control-height) / 2);

  display: inline-flex;
  flex-wrap: nowrap;
  align-items: stretch;
  gap: 0.125rem;
  height: var(--zs-control-height);
  padding: 0;
  border: var(--period-picker-border) solid $zs-border-input;
  border-radius: var(--period-picker-radius);
  background: #fff;
}

.period-picker__label {
  display: flex;
  align-items: center;
  margin: 0 0.375rem 0 0.75rem;
  color: $zs-text-muted;
  font-size: 0.8125rem;
  white-space: nowrap;
}

// Группа кнопок сама рамки и скруглений не рисует: каждая кнопка — свой сегмент-пилюля
.period-picker__short {
  height: 100%;
  border-radius: 0;
  box-shadow: none;
  gap: 0.125rem;

  :deep(.q-btn) {
    min-height: 0;
    height: 100%;
    padding: 0 0.75rem;
    border-radius: calc(var(--period-picker-radius) - var(--period-picker-border));
    color: $zs-text;
    font-size: 0.8125rem;
    font-weight: 400;
  }

  // фокус с клавиатуры — как у CbButton и CbSegment: обычная рамка фокуса браузера вместо заливки Quasar
  :deep(.q-btn:focus-visible) {
    outline: revert;
    outline-offset: revert;
  }

  :deep(.q-btn:focus-visible > .q-focus-helper) {
    opacity: 0;
  }
}

// Кнопка «...»: боковых отступов ей не нужно, у иконки своё внутреннее поле
.period-picker__more {
  min-width: 0;
  min-height: 0;
  height: 100%;
  padding: 0 0.5rem;
  border-radius: calc(var(--period-picker-radius) - var(--period-picker-border));
  color: $zs-text-muted;
}

.period-picker__quick :deep(.q-btn) {
  color: $zs-text;
}

.period-picker__calendars {
  border-radius: 0.25rem;
}

.period-picker__error {
  color: #8f2416;
  font-size: 0.8125rem;
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

// Две колонки в горизонтальном режиме. Flex-колонка переносится только при ограниченной высоте, а её тут нет,
// поэтому берём multi-column: он сам делит список поровну
.quick-toggle-2col {
  display: block;
  column-count: 2;
  column-gap: 0.5rem;
}

.quick-toggle-2col :deep(.q-btn) {
  display: flex;
  width: 100%;
  break-inside: avoid;
}

// Обычная раскладка: календари стоят рядом, между ними — разделитель
.custom-date--start:not(.custom-date-mobile) {
  border-right: 0.125rem solid $zs-border-row;
}

.custom-date :deep(.q-date__header) {
  background: $zs-bg-header;
  color: $zs-text;
}

.custom-date :deep(.q-date__header-title-label) {
  color: $primary;
  font-size: 1.2rem;
}

// Заглушка «не выбрана» вместо даты — светлее самой даты
.date-empty :deep(.q-date__header-title-label) {
  color: $zs-text-faint;
}

// Год: opacity сбрасываем, иначе q-date__header-link (.64) осветляет заданный цвет
.custom-date :deep(.q-date__header-subtitle) {
  color: $primary;
  opacity: 1;
}

.custom-date :deep(.q-date__actions) {
  padding: 0;
}

// Место в шапке под строку с подписью и кнопкой «Очистить»: высота шапки в portrait жёстко задана Quasar-ом, поэтому
// увеличиваем её ровно на высоту вставленной строки
.custom-date :deep(.q-date__header) {
  height: 6.9rem;
  padding-top: 2.5rem;
}

// Календарь занимает свою естественную ширину: по умолчанию flex-элемент сжимается (flex-shrink: 1), и шапка с
// подписью и «Очистить» переносится на две строки, хотя место по бокам есть
.date-col-mobile {
  flex: 0 0 auto;
}

// Панель готовых периодов сама по себе уже календаря, а обе панели подменяются в одном попапе: без общего минимума он
// прыгает по ширине при переходе к календарям
.quick-panel-compact {
  min-width: 15rem;
}

// Зазор между календарями, когда они стоят в строку
.date-col-gap {
  padding-right: 0.2rem;
}

// Ниже .date-col-gap: специфичность одинаковая, побеждает порядок
.date-col-gap-wide {
  padding-right: 0.6rem;
}

// Дальше — только компактный режим (класс custom-date-mobile вешается по isCompact()). Quasar задаёт календарю width
// и min-width (QDate.sass) — снимаем обе, ширину определяет содержимое (у .q-date__content она задана ниже)
.custom-date-mobile {
  width: auto;
  min-width: 18rem;
}

// Шапка по умолчанию — блок, поэтому год стоит отдельной строкой над датой. Переводим её во flex и меняем порядок:
// год встаёт сразу после даты — освободившаяся строка уменьшает высоту шапки
.custom-date-mobile :deep(.q-date__header) {
  display: flex;
  align-items: baseline;
  height: 4.5rem;
  padding-top: 2.3rem;
  padding-bottom: 0.375rem;
}

.custom-date-mobile :deep(.date-header-line) {
  height: 2.3rem;
}

// Блок с числами (навигация, дни недели, сетка дней): у Quasar большой отступ и минимальные высоты — снимаем их,
// вместо отступа — небольшой
.custom-date-mobile :deep(.q-date__view) {
  min-height: 0;
  padding: 0.25rem;
}

.custom-date-mobile :deep(.q-date__calendar-days-container) {
  min-height: 0;
}

// q-date в portrait — flex-колонка, поэтому по горизонтали блок центрируем через align-self
.custom-date-mobile :deep(.q-date__content) {
  width: 15rem;
  align-self: center;
}

// Первый ребёнок шапки — обёртка года, второй — .q-date__header-title (см. QDate.js, getHeader)
.custom-date-mobile :deep(.q-date__header) > div:first-child {
  order: 2;
  margin-left: 0.5rem;
}

.custom-date-mobile :deep(.q-date__header-title) {
  order: 1;
  flex: 0 0 auto;
}

// Внутри title лежит .col (flex: 10000 1 0%) — без этого он растянулся бы на всю ширину шапки, и год уехал бы к
// правому краю
.custom-date-mobile :deep(.q-date__header-title > .col) {
  flex: 0 0 auto;
}

// Строка приходит из default-слота (низ q-date), поэтому позиционируется абсолютом относительно самого q-date (у него
// relative-position)
.date-header-line {
  position: absolute;
  top: 0;
  right: 0;
  left: 0;
  z-index: 10;
  height: 2.5rem;
  padding: 0.25rem 0.5rem 0 1rem;
}

.date-header-line__text {
  color: $zs-text;
  font-size: 0.9375rem;
  font-weight: 600;
}

// Боковые кнопки — по углам карточки, поверх календарей. В потоке каждая занимала бы колонку во всю высоту строки и
// сдвигала календари, а ширину кнопки применения пришлось бы ограничивать
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

// Там, где у карточки есть поля, кнопки в углах отодвигаем на ту же величину: прижатые вплотную к краю они выпадают
// из макета. Отсчёт у абсолютных элементов идёт от padding-box карточки, поэтому отступ задаём явно
.period-card-padded .period-list,
.period-card-padded .period-apply {
  bottom: 1rem;
}

.period-card-padded .period-list {
  left: 1rem;
}

.period-card-padded .period-apply {
  right: 1rem;
}

// Кнопка возврата к готовым периодам — в углу напротив применения, растёт вправо
.period-list {
  position: absolute;
  bottom: 0;
  left: 0;
  z-index: 10;
  white-space: nowrap;
}

// Полоса под нижними кнопками: их ширина не ограничена, поэтому уводим календари целиком выше, а не пытаемся
// разойтись с ними по горизонтали. Отступ — высота обычной кнопки
.period-calendars-bottom {
  padding-bottom: 2.3rem;
}
</style>
