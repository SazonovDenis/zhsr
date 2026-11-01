<template>
  <!-- крестики сброса Tab пропускает: с клавиатуры значение стирают в самом поле -->
  <div ref="rootEl" class="cb-list" :class="{ 'cb-list--no-search': noSearch }" @focusout="onFocusOut">
    <!-- поле отображения; mousedown мимо поля ввода не забирает у него фокус — иначе список закрылся бы -->
    <div
      class="cb-list__field"
      :class="{ 'cb-list__field--focused': isOpen && !noSearch }"
      @mousedown="keepFocus"
      @click="onFieldClick"
    >
      <!-- single: поле поиска совмещено с показом выбранного -->
      <template v-if="single">
        <!-- без поиска поле ввода не нужно: выбранное показывается обычным текстом -->
        <span v-if="noSearch" class="cb-list__text" :class="{ 'cb-list__text--placeholder': selected.length === 0 }">
          <slot v-if="valueShown" name="value" :item="selected[0]!" />
          <template v-else>{{ selected.length > 0 ? singleDisplay : placeholderText }}</template>
        </span>
        <!-- выбранное своим видом (слот value): поле ввода остаётся под ним невидимым — в него приходят фокус и ввод -->
        <div v-else-if="valueShown" class="cb-list__value">
          <slot name="value" :item="selected[0]!" />
        </div>
        <input
          v-if="!noSearch"
          ref="singleInput"
          class="cb-list__input zs-focusable"
          :class="{ 'cb-list__input--behind': valueShown }"
          :value="isOpen ? query : singleDisplay"
          :placeholder="placeholderText"
          @input="onSingleInput"
          @keydown="onKeydown"
        />
        <!-- место под крестик есть всегда: появляясь и исчезая, он не меняет ширину поля -->
        <button
          type="button"
          tabindex="-1"
          class="cb-list__clear"
          :class="{ 'cb-list__clear--hidden': !(selected.length > 0 && !isDisabled(selected[0])) }"
          :title="t('Очистить')"
          @click.stop="clearAll"
        >
          ✕
        </button>
      </template>

      <!-- multi: выбранные значения чипами -->
      <div v-else class="cb-list__chips">
        <template v-if="selected.length > 0">
          <span v-for="item in selected" :key="item.id" class="cb-list__chip">
            <span class="cb-list__chip-text">{{ displayOf(item) }}</span>
            <button
              v-if="!isDisabled(item)"
              type="button"
              tabindex="-1"
              class="cb-list__clear"
              @click.stop="removeItem(item)"
            >
              ✕
            </button>
          </span>
        </template>
        <span v-else class="cb-list__placeholder">{{ placeholderText }}</span>
      </div>

      <SvgIcon
        v-if="showArrow"
        name="chevron-down"
        size="0.8rem"
        class="cb-list__arrow"
        :class="{ 'cb-list__arrow--open': isOpen }"
        @click.stop="toggleDropdown"
      />
    </div>

    <!-- выпадающий список -->
    <Teleport to="body">
      <div
        v-if="isOpen"
        ref="dropdownEl"
        class="cb-list__dropdown"
        :style="dropdownStyle"
        @mousedown.stop="keepFocus"
        @focusout="onFocusOut"
      >
        <div v-if="showSearch" class="cb-list__search">
          <q-input
            ref="multiInput"
            v-model="query"
            dense
            outlined
            :placeholder="t(customPlaceholder || 'Поиск...')"
            @keydown="onKeydown"
            @click.stop
          >
            <!-- свой крестик вместо clearable: крестик Quasar — кнопка, и Tab останавливался бы на нём -->
            <template #append>
              <q-icon v-if="query" name="cancel" class="cursor-pointer" @click.stop="query = ''" />
            </template>
          </q-input>
        </div>

        <!-- добавление свободного значения (allowCustom) -->
        <div v-if="customCandidates.length > 0" class="cb-list__option cb-list__add-custom" @click.stop="addCustom">
          <q-icon name="add" size="1rem" />
          <span>{{ t('Добавить') }}: {{ customCandidates.join(', ') }}</span>
        </div>

        <div ref="optionsEl" class="cb-list__options">
          <div
            v-for="(item, index) in filtered"
            :key="item.id"
            class="cb-list__option"
            :class="{
              'cb-list__option--selected bg-selected': single && isSelected(item),
              'cb-list__option--highlighted': index === highlighted,
              'cb-list__option--disabled': isDisabled(item),
            }"
            @click.stop="onOptionClick(item)"
            @mouseenter="highlighted = index"
          >
            <q-checkbox
              v-if="!single"
              :model-value="isSelected(item)"
              dense
              :disable="isDisabled(item)"
              @update:model-value="onOptionClick(item)"
              @click.stop
            />
            <span class="cb-list__option-body">
              <slot name="option" :item="item"><CbOption :item="item" /></slot>
            </span>
          </div>
          <div v-if="filtered.length === 0 && customCandidates.length === 0" class="cb-list__no-options">
            {{ t('Нет вариантов') }}
          </div>
        </div>

        <div v-if="!single" class="cb-list__footer">
          <q-btn no-caps dense color="primary" class="q-px-md" :label="t('Ок')" @click.stop="closeDropdown" />
        </div>
      </div>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
import type { QInput } from 'quasar'
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'

import { t } from '@/boot/i18n'
import { isMobile } from '@/shared/device'
import SvgIcon from '@/shared/icons/SvgIcon.vue'
import { screenScale } from '@/shared/screen'
import type { ChoiceItem, ChoiceModel } from './choice'
import CbOption from './CbOption.vue'
import { displayOf, effectiveOf, isDisabled, labelOf, modelIds, modelOf, nextEnabledIndex } from './choice'

/**
 * CbList — поле выбора с выпадающим списком и поиском.
 *
 *   <CbList v-model="states" :values="items" />
 *   <CbList v-model="station" :values="items" single placeholder="Станция" />
 *   <CbList v-model="codes" :values="items" allow-custom :custom-parse="fn" />
 *
 * Правила values, v-model и change — см. choice.ts.
 *
 * Поиск: при single текст вводится в само поле контрола, при множественном выборе — в строку поиска в списке
 * (если вариантов больше showSearchAbove).
 *
 * Открытие и закрытие: фокус в поле (например, по Tab) список не открывает. Его открывают клик по полю, начало ввода
 * и стрелка вниз. Фокус ушёл из поля и списка — список закрывается.
 *
 * noSearch: искать нельзя, поля ввода нет ни в поле, ни в списке. Выбранное показывается обычным текстом без рамки.
 *
 * На маленьком экране в single при выбранном значении стрелка не показывается: место занимает крестик сброса, а
 * список открывается кликом по полю.
 *
 * allowCustom: разрешает значения, которых нет в values. Текст из строки поиска превращается в значения
 * (customParse) и добавляется по Enter или клику на «Добавить». Такие значения хранятся как
 * { id, text, custom: true } и уходят в модель наравне с обычными.
 *
 * Слот option — свой вид строки списка, например с пометкой и числом справа; данные для него — в item.data.
 * Слот value (single) — свой вид выбранного значения в поле, пока список закрыт; с началом ввода на его месте
 * обычное поле поиска. item.search — дополнительный текст для поиска: «Карага» находит маршрут через Караганду.
 * Сложный вид строки собирается в предметном компоненте поверх CbList (пример — RouteChoice), сам CbList про
 * предметную область не знает.
 * Событие open — список открывается: значения, которые зависят от обстоятельств, можно загрузить в этот момент.
 */
const props = withDefaults(
  defineProps<{
    single?: boolean
    values?: ChoiceItem[]
    modelValue?: ChoiceModel
    /** текст на русском для пустого поля; переводится при показе */
    placeholder?: string
    /** строка поиска показывается, если вариантов больше этого числа */
    showSearchAbove?: number
    allowCustom?: boolean
    /** превращает введённый текст в массив свободных значений; по умолчанию — разбиение по запятой и пробелу */
    customParse?: ((text: string) => (string | number)[]) | null
    /** текст строки ввода вместо «Поиск...» */
    customPlaceholder?: string
    noSearch?: boolean
  }>(),
  {
    single: false,
    values: () => [],
    modelValue: null,
    placeholder: '',
    showSearchAbove: 5,
    allowCustom: false,
    customParse: null,
    customPlaceholder: '',
    noSearch: false,
  },
)

const emit = defineEmits<{
  'update:modelValue': [value: ChoiceModel]
  change: [values: unknown[]]
  /** список открывается */
  open: []
}>()

const slots = defineSlots<{
  option?: (props: { item: ChoiceItem }) => unknown
  value?: (props: { item: ChoiceItem }) => unknown
}>()

const rootEl = ref<HTMLElement | null>(null)
const dropdownEl = ref<HTMLElement | null>(null)
const optionsEl = ref<HTMLElement | null>(null)
const singleInput = ref<HTMLInputElement | null>(null)
const multiInput = ref<QInput | null>(null)

// объекты выбранных значений — единственный источник правды
const selected = ref<ChoiceItem[]>([])
const isOpen = ref(false)
const query = ref<string | null>('')
const highlighted = ref(-1)
const dropdownStyle = ref<Record<string, string>>({})

/** эффективные значения выбранного */
const effective = computed(() => selected.value.flatMap((item) => effectiveOf(item)))

/** варианты списка: values плюс уже выбранные свободные значения */
const options = computed(() => {
  if (!props.allowCustom) return props.values
  const custom = selected.value.filter((item) => item.custom)
  return custom.length === 0 ? props.values : [...props.values, ...custom]
})

const filtered = computed(() => {
  const q = (query.value ?? '').toLowerCase()
  if (!q) return options.value
  return options.value.filter((item) => matches(item, q))
})

const singleDisplay = computed(() => (selected.value[0] ? displayOf(selected.value[0]) : ''))

/** Выбранное показывается слотом value: он задан, значение выбрано и список закрыт. */
const valueShown = computed(() => props.single && !!slots.value && !isOpen.value && selected.value.length > 0)

const placeholderText = computed(() => (props.placeholder ? t(props.placeholder) : ''))

/**
 * Строка поиска в выпадающем списке — только при множественном выборе. При одиночном выборе текст поиска вводится в
 * само поле контрола, и вторая строка ввода была бы лишней.
 */
const showSearch = computed(() => {
  if (props.single) return false
  // без поиска свободные значения вводить тоже нечем
  if (props.noSearch) return false
  if (props.allowCustom) return true
  return props.values.length > props.showSearchAbove
})

/**
 * Стрелка списка. Прячется только на маленьком экране при одиночном выборе с выбранным значением: рядом крестик
 * сброса, и две кнопки подряд не помещаются. Список открывается кликом по полю.
 */
const showArrow = computed(() => {
  if (!props.single || screenScale() > 0) return true
  return selected.value.length === 0
})

/**
 * Свободные значения из строки ввода: без пустых, дублей и уже покрытых выбором. Покрытое значение ничего не
 * изменит в результате: «doc» при выбранном варианте с value ["docx", "doc"].
 */
const customCandidates = computed(() => {
  const text = query.value ?? ''
  if (!props.allowCustom || !text) return []
  const parsed = props.customParse ? props.customParse(text) : defaultCustomParse(text)
  const result: (string | number)[] = []
  for (const val of parsed) {
    if (val === null || val === undefined || val === '') continue
    if (result.some((v) => String(v) === String(val))) continue
    if (effective.value.some((v) => String(v) === String(val))) continue
    result.push(val)
  }
  return result
})

watch(() => props.modelValue, syncSelected, { immediate: true })
watch(() => props.values, syncSelected)
watch(query, () => (highlighted.value = -1))

// --- синхронизация с modelValue ---

function syncSelected() {
  const list: ChoiceItem[] = []
  for (const id of modelIds(props.modelValue)) {
    const item = props.values.find((v) => v.id === id)
    if (item) {
      list.push(item)
    } else if (props.allowCustom) {
      // значения нет в values — сохраняем как свободное, иначе оно потерялось бы
      list.push({ id, text: String(id), custom: true })
    }
  }
  selected.value = list
}

function emitChange() {
  emit('update:modelValue', modelOf(selected.value, props.single))
  emit('change', effective.value)
}

// --- вспомогательные ---

/**
 * Выбран ли вариант. Для вариантов с value совпадение по id со свободным значением не считается: иначе набранное
 * вручную «docx» отмечало бы галочку группы.
 */
function isSelected(item: ChoiceItem): boolean {
  if (item.value !== undefined && item.value !== null) {
    return selected.value.some((s) => s.id === item.id && !s.custom)
  }
  return selected.value.some((s) => s.id === item.id)
}

/** Совпадение со строкой поиска: по тексту, переводу, id и эффективным значениям («jpg» находит «Изображения»). */
function matches(item: ChoiceItem, q: string): boolean {
  if (labelOf(item).toLowerCase().includes(q)) return true
  if (displayOf(item).toLowerCase().includes(q)) return true
  if (String(item.id).toLowerCase().includes(q)) return true
  if (item.search?.toLowerCase().includes(q)) return true
  if (item.value === undefined || item.value === null) return false
  return effectiveOf(item).some((v) => String(v).toLowerCase().includes(q))
}

/** «rar, zip psd» → ["rar", "zip", "psd"] */
function defaultCustomParse(text: string): string[] {
  return text.split(/[,;\s]+/).map((part) => part.trim())
}

/**
 * Добавляет введённые значения в выбор. Если текст совпал с id настоящего варианта, выбирается сам вариант — иначе
 * получился бы свободный дубликат с тем же id.
 */
function addCustom() {
  const candidates = customCandidates.value
  if (candidates.length === 0) return
  let next = selected.value
  for (const val of candidates) {
    let item = props.values.find((v) => String(v.id) === String(val))
    // недоступный вариант нельзя выбрать и обойти запрет вводом текста
    if (item && isDisabled(item)) continue
    if (!item) item = { id: String(val), text: String(val), custom: true }
    next = addWithSubsumption(next, item)
  }
  selected.value = next
  emitChange()
  query.value = ''
}

/**
 * Добавляет вариант, устраняя избыточность в обе стороны: убирает выбранное, что целиком покрывается новым
 * вариантом, и выбранное, что само целиком покрывает новый вариант.
 */
function addWithSubsumption(list: ChoiceItem[], item: ChoiceItem): ChoiceItem[] {
  const itemValues = effectiveOf(item).map(String)
  const kept = list.filter((s) => {
    const sValues = effectiveOf(s).map(String)
    const sCoveredByItem = sValues.every((v) => itemValues.includes(v))
    const itemCoveredByS = itemValues.every((v) => sValues.includes(v))
    return !sCoveredByItem && !itemCoveredByS
  })
  return [...kept, item]
}

// --- выпадающий список ---

function onFieldClick() {
  // в multi и без поиска клик открывает и закрывает список: фокусировать нечего
  if (!props.single || props.noSearch) {
    toggleDropdown()
    return
  }
  // в single клик открывает список и ставит фокус в поле ввода
  if (!isOpen.value) openDropdown()
}

/**
 * Ввод в поле single. Закрытый список ввод открывает: набранный знак начинает поиск заново, а не дописывается к
 * показанному значению. Остальное (удаление, вставка) ищет по тексту поля как есть.
 */
function onSingleInput(e: Event) {
  const text = (e.target as HTMLInputElement).value
  if (isOpen.value) {
    query.value = text
    return
  }
  const event = e as InputEvent
  openDropdown()
  query.value = event.inputType === 'insertText' && event.data ? event.data : text
}

/** mousedown в поле и списке не забирает фокус у поля ввода: иначе фокус ушёл бы, и список закрылся. */
function keepFocus(e: MouseEvent) {
  const target = e.target as HTMLElement
  if (!target.closest('input, button')) e.preventDefault()
}

/** Фокус ушёл из поля и списка — список закрывается; переход фокуса между ними список не трогает. */
function onFocusOut(e: FocusEvent) {
  const next = e.relatedTarget as Node | null
  if (next && (rootEl.value?.contains(next) || dropdownEl.value?.contains(next))) return
  closeDropdown()
}

function toggleDropdown() {
  if (isOpen.value) closeDropdown()
  else openDropdown()
}

function openDropdown() {
  isOpen.value = true
  emit('open')
  query.value = ''
  highlighted.value = -1
  // первый тик — список отрисован (считаем положение), второй — фокус
  void nextTick(() => {
    calcDropdownStyle()
    // на телефоне фокус поднял бы экранную клавиатуру, она меняет вьюпорт, и список закрылся бы сразу
    if (props.noSearch || isMobile.value) return
    void nextTick(() => {
      if (props.single) singleInput.value?.focus()
      else multiInput.value?.focus()
    })
  })
}

function closeDropdown() {
  isOpen.value = false
  query.value = ''
}

function calcDropdownStyle() {
  const root = rootEl.value
  if (!root) return

  const rem = parseFloat(getComputedStyle(document.documentElement).fontSize)
  const rect = root.getBoundingClientRect()
  const spaceBelow = window.innerHeight - rect.bottom
  const spaceAbove = rect.top
  const gap = 0.5 * rem

  const openBelow = spaceBelow >= 7.5 * rem || spaceBelow >= spaceAbove
  const vertical = openBelow
    ? { top: `${rect.bottom}px`, maxHeight: `${spaceBelow - gap}px` }
    : { top: 'auto', bottom: `${window.innerHeight - rect.top}px`, maxHeight: `${spaceAbove - gap}px` }
  const horizontal =
    window.innerWidth - rect.left >= 12.5 * rem
      ? { left: `${rect.left}px` }
      : { left: 'auto', right: `${window.innerWidth - rect.right}px` }

  // положение считается от измеренного поля, поэтому в пикселях
  dropdownStyle.value = { position: 'fixed', minWidth: `${rect.width}px`, ...vertical, ...horizontal }
}

// --- выбор ---

function onOptionClick(item: ChoiceItem | undefined) {
  if (!item || isDisabled(item)) return

  if (props.single) {
    selected.value = [item]
    emitChange()
    closeDropdown()
  } else if (isSelected(item)) {
    selected.value = selected.value.filter((s) => s.id !== item.id)
    emitChange()
  } else {
    selected.value = addWithSubsumption(selected.value, item)
    emitChange()
  }
}

function removeItem(item: ChoiceItem) {
  if (isDisabled(item)) return
  selected.value = selected.value.filter((s) => s.id !== item.id)
  emitChange()
}

// недоступное значение снять нельзя, поэтому «очистить» оставляет именно его
function clearAll() {
  selected.value = selected.value.filter((s) => isDisabled(s))
  emitChange()
}

// --- клавиатура ---

function onKeydown(e: KeyboardEvent) {
  const items = filtered.value
  const stop = () => {
    e.preventDefault()
    e.stopPropagation()
  }

  if (!isOpen.value) {
    // закрытый список открывает стрелка вниз; курсор встаёт на выбранное значение
    if (e.key === 'ArrowDown') {
      stop()
      openDropdown()
      highlighted.value = Math.max(
        0,
        filtered.value.findIndex((item) => isSelected(item)),
      )
      scrollHighlighted()
    }
    return
  }

  if (e.key === 'ArrowDown') {
    stop()
    highlighted.value = nextEnabledIndex(items, highlighted.value, 1)
    scrollHighlighted()
  } else if (e.key === 'ArrowUp') {
    stop()
    // курсора ещё нет или он на первом значении — встаём на первое доступное
    highlighted.value =
      highlighted.value <= 0 ? nextEnabledIndex(items, -1, 1) : nextEnabledIndex(items, highlighted.value, -1)
    scrollHighlighted()
  } else if (e.key === 'Escape') {
    e.stopPropagation()
    closeDropdown()
  } else if (e.key === 'Enter') {
    stop()
    const current = items[highlighted.value]
    if (props.single) {
      // курсор ни на чём — берём первое доступное значение
      onOptionClick(current ?? items[nextEnabledIndex(items, -1, 1)])
    } else if (!current && customCandidates.value.length > 0) {
      // введено свободное значение — добавляем его, список остаётся открытым
      addCustom()
    } else {
      if (current && !isSelected(current)) onOptionClick(current)
      closeDropdown()
    }
  } else if (e.key === ' ' && items[highlighted.value]) {
    stop()
    onOptionClick(items[highlighted.value])
  }
}

function scrollHighlighted() {
  void nextTick(() => {
    optionsEl.value?.querySelectorAll('.cb-list__option')[highlighted.value]?.scrollIntoView({ block: 'nearest' })
  })
}

// --- закрытие при прокрутке и клике мимо ---

function onWindowScroll(e: Event) {
  if (dropdownEl.value?.contains(e.target as Node)) return
  closeDropdown()
}

/**
 * Размер окна изменился — список не закрываем, а пересчитываем положение: на телефоне вьюпорт меняет экранная
 * клавиатура, и список закрывался бы сразу после открытия.
 */
function onWindowResize() {
  if (isOpen.value) calcDropdownStyle()
}

function onOutsideClick(e: MouseEvent) {
  const target = e.target as Node
  if (rootEl.value?.contains(target) || dropdownEl.value?.contains(target)) return
  closeDropdown()
}

onMounted(() => {
  document.addEventListener('mousedown', onOutsideClick)
  window.addEventListener('scroll', onWindowScroll, true)
  window.addEventListener('resize', onWindowResize)
})

onBeforeUnmount(() => {
  document.removeEventListener('mousedown', onOutsideClick)
  window.removeEventListener('scroll', onWindowScroll, true)
  window.removeEventListener('resize', onWindowResize)
})
</script>

<style scoped lang="scss">
.cb-list {
  position: relative;
  display: inline-block;
  min-width: 0;
}

.cb-list__field {
  position: relative;
  display: flex;
  flex-wrap: nowrap;
  align-items: center;
  gap: 0.375rem;
  min-height: var(--zs-control-height);
  padding: 0.125rem 0.625rem;
  border: 0.0625rem solid $zs-border-input;
  border-radius: 0.25rem;
  background: #fff;
  font-size: 0.875rem;
  cursor: pointer;

  // фокус в поле виден и при закрытом списке: под слотом value курсора ввода нет
  &--focused,
  .cb-list:not(.cb-list--no-search) &:focus-within {
    border-color: $primary;
    box-shadow: 0 0 0 0.0625rem $primary;
  }
}

.cb-list__value {
  flex: 1;
  min-width: 0;
}

.cb-list__input {
  flex: 1;
  min-width: 0;
  width: 100%;
  border: none;
  outline: none;
  background: transparent;
  font: inherit;
  color: $zs-text;
  cursor: text;

  &::placeholder {
    color: $zs-text-faint;
    opacity: 1;
  }

  // под слотом value: невидимо и мимо мыши, но фокус и ввод принимает
  &--behind {
    position: absolute;
    inset: 0;
    opacity: 0;
    pointer-events: none;
  }
}

.cb-list__placeholder {
  color: $zs-text-faint;
  user-select: none;
}

.cb-list__text {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  user-select: none;

  &--placeholder {
    color: $zs-text-faint;
  }
}

// без поиска рамки нет: печатать в поле нельзя, и вид поля ввода только обманывал бы
.cb-list--no-search .cb-list__field {
  padding-left: 0;
  border-color: transparent;
  background: transparent;
}

.cb-list__chips {
  display: flex;
  flex: 1;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.125rem 0.25rem;
  min-width: 0;
}

.cb-list__chip {
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
  padding: 0.0625rem 0.125rem 0.0625rem 0.5rem;
  border-radius: 0.75rem;
  background: $zs-bg-button-soft;
  font-size: 0.8125rem;
  white-space: nowrap;
}

.cb-list__chip-text {
  max-width: 10rem;
  overflow: hidden;
  text-overflow: ellipsis;
}

// крестик сброса по макету: круглая кнопка с ✕
.cb-list__clear {
  display: flex;
  flex: none;
  align-items: center;
  justify-content: center;
  width: 1.1rem;
  height: 1.1rem;
  padding: 0;
  border: 0;
  border-radius: 50%;
  background: #eef1f4;
  color: $zs-text-faint;
  font-size: 0.5625rem;
  font-weight: 700;
  line-height: 1;
  cursor: pointer;

  &:hover {
    background: #e1e6eb;
    color: $zs-text-muted;
  }
}

.cb-list__clear--hidden {
  visibility: hidden;
}

.cb-list__arrow {
  color: $zs-text-muted;
  transition: transform 0.15s;

  &--open {
    transform: rotate(180deg);
  }
}

.cb-list__dropdown {
  z-index: 9000;
  display: flex;
  flex-direction: column;
  // ширина — по содержимому, не уже поля (min-width ставит calcDropdownStyle); длинные строки ограничивает сам
  // вид строки (слот option), а не список
  width: max-content;
  overflow: hidden;
  border: 0.0625rem solid $zs-border;
  border-radius: 0.25rem;
  background: #fff;
  box-shadow: 0 0.75rem 1.875rem rgba(0, 0, 0, 0.14);
}

.cb-list__search {
  padding: 0.25rem;
  border-bottom: 0.0625rem solid #f0f3f5;
}

.cb-list__options {
  flex: 1;
  overflow-y: auto;
}

.cb-list__option {
  display: flex;
  flex-wrap: nowrap;
  align-items: center;
  gap: 0.25rem;
  padding: 0.4375rem 0.75rem;
  font-size: 0.875rem;
  cursor: pointer;

  // выбранная строка — общим фоном bg-selected (app.scss), строка под курсором — фоном hover
  &:hover,
  &--highlighted {
    background: $zs-bg-hover;
  }

  // значение видно, но выбрать его нельзя: строка под курсором не подсвечивается
  &--disabled {
    color: #9e9e9e;
    cursor: default;

    &:not(.cb-list__option--selected):hover,
    &:not(.cb-list__option--selected).cb-list__option--highlighted {
      background: transparent;
    }
  }
}

.cb-list__option-body {
  flex: 1;
  min-width: 0;
}

.cb-list__add-custom {
  border-bottom: 0.0625rem solid #f0f3f5;
  color: $primary;
}

.cb-list__no-options {
  padding: 0.5rem;
  color: $zs-text-faint;
  text-align: center;
}

.cb-list__footer {
  display: flex;
  justify-content: flex-end;
  padding: 0.25rem;
  border-top: 0.0625rem solid #f0f3f5;
}
</style>
