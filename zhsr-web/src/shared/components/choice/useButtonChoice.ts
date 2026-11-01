import { computed, ref, watch } from 'vue'

import type { ChoiceId, ChoiceItem, ChoiceModel } from './choice'
import { effectiveOf, isDisabled, modelIds, modelOf } from './choice'

/** Свойства, общие для CbButton и CbSegment. */
export interface ButtonChoiceProps {
  /** одиночный выбор, иначе множественный */
  single?: boolean
  /** хотя бы одно значение должно быть выбрано */
  required?: boolean
  values?: ChoiceItem[]
  /** id значений, которые не показываются кнопками (доступны только через «...») */
  hidden?: ChoiceId[]
  /** сколько значений показывать кнопками до появления «...» */
  maxButtonCount?: number
  modelValue?: ChoiceModel
}

export interface ButtonChoiceEmit {
  (e: 'update:modelValue', value: ChoiceModel): void
  (e: 'change', values: unknown[]): void
}

/**
 * Логика ряда кнопок с «...»: что показывать кнопками, что убирать в меню и как меняется выбор.
 * Общая для CbButton и CbSegment — они отличаются только видом.
 */
export function useButtonChoice(props: ButtonChoiceProps, emit: ButtonChoiceEmit) {
  const values = computed(() => props.values ?? [])
  const maxCount = computed(() => props.maxButtonCount ?? 3)

  // объекты выбранных значений — единственный источник правды
  const selected = ref<ChoiceItem[]>([])
  // id значений, показываемых кнопками
  const visibleOrder = ref<ChoiceId[]>([])

  const visibleValues = computed(() =>
    visibleOrder.value.flatMap((id) => values.value.filter((v) => v.id === id).slice(0, 1)),
  )

  const hiddenValues = computed(() => {
    const visibleSet = new Set(visibleOrder.value)
    return values.value.filter((item) => !visibleSet.has(item.id))
  })

  function isSelected(item: ChoiceItem): boolean {
    return selected.value.some((s) => s.id === item.id)
  }

  function initVisibleOrder() {
    const hiddenSet = new Set(props.hidden ?? [])
    const selectedIds = new Set(modelIds(props.modelValue))
    const candidates = values.value.filter((v) => !hiddenSet.has(v.id))

    // всё нескрытое влезает в ряд — порядок остаётся словарным, и кнопки не переставляются при смене выбора
    if (candidates.length <= maxCount.value) {
      visibleOrder.value = candidates.map((v) => v.id)
      return
    }

    // не влезает — выбранное показываем первым, иначе оно оказалось бы под «...»
    const selectedVisible = candidates.filter((v) => selectedIds.has(v.id)).map((v) => v.id)
    const slots = Math.max(0, maxCount.value - selectedVisible.length)
    const rest = candidates
      .filter((v) => !selectedIds.has(v.id))
      .slice(0, slots)
      .map((v) => v.id)
    visibleOrder.value = [...selectedVisible, ...rest]
  }

  function syncSelected() {
    const ids = modelIds(props.modelValue)
    selected.value = values.value.filter((v) => ids.includes(v.id))
  }

  /** Убирает из ряда лишние невыбранные кнопки сверх maxButtonCount. */
  function trimVisibleOrder(selectedSet: Set<ChoiceId>): ChoiceId[] {
    let order = [...visibleOrder.value]
    while (order.length > maxCount.value) {
      const removable = order.find((id) => !selectedSet.has(id))
      if (removable === undefined) break
      order = order.filter((id) => id !== removable)
    }
    return order
  }

  function selectedIdSet(): Set<ChoiceId> {
    return new Set(selected.value.map((s) => s.id))
  }

  function emitChange() {
    emit('update:modelValue', modelOf(selected.value, !!props.single))
    emit(
      'change',
      selected.value.flatMap((item) => effectiveOf(item)),
    )
  }

  function onButtonClick(item: ChoiceItem) {
    if (isDisabled(item)) return

    const wasSelected = isSelected(item)
    if (props.single) {
      selected.value = wasSelected ? [] : [item]
    } else {
      selected.value = wasSelected ? selected.value.filter((s) => s.id !== item.id) : [...selected.value, item]
    }

    if (props.required && selected.value.length === 0) {
      const other = values.value.find((v) => v.id !== item.id && !isDisabled(v))
      if (other) selected.value = [other]
    }

    if (wasSelected) visibleOrder.value = trimVisibleOrder(selectedIdSet())
    emitChange()
  }

  /** Выбор в меню «...»: выбранное значение встаёт в ряд кнопок. */
  function onMenuPick(item: ChoiceItem) {
    if (isDisabled(item)) return

    if (props.single) {
      if (!visibleOrder.value.includes(item.id)) visibleOrder.value = [...visibleOrder.value, item.id]
      visibleOrder.value = trimVisibleOrder(new Set([item.id]))
      selected.value = [item]
    } else {
      const wasSelected = isSelected(item)
      selected.value = wasSelected ? selected.value.filter((s) => s.id !== item.id) : [...selected.value, item]
      if (!wasSelected && !visibleOrder.value.includes(item.id)) {
        visibleOrder.value = [...visibleOrder.value, item.id]
      }
      visibleOrder.value = trimVisibleOrder(selectedIdSet())
    }
    emitChange()
  }

  watch(
    () => props.hidden,
    (newVal, oldVal) => {
      const serialize = (arr?: ChoiceId[]) => [...(arr ?? [])].sort().join('\0')
      if (oldVal === undefined || serialize(newVal) !== serialize(oldVal)) initVisibleOrder()
    },
    { immediate: true },
  )

  watch(values, () => {
    initVisibleOrder()
    syncSelected()
  })

  watch(() => props.modelValue, syncSelected, { immediate: true })

  // --- клавиатура ---

  // кнопка, на которой последний раз был фокус
  const focusedId = ref<ChoiceId | null>(null)

  /**
   * Кнопка, на которую встаёт Tab: ряд — одна остановка. Последняя кнопка с фокусом, иначе выбранная, иначе первая
   * доступная. Остальные кнопки и «...» Tab пропускает: по ряду ходят стрелками.
   */
  const tabStopId = computed<ChoiceId | null>(() => {
    const enabled = visibleValues.value.filter((v) => !isDisabled(v))
    const focused = enabled.find((v) => v.id === focusedId.value)
    return (focused ?? enabled.find((v) => isSelected(v)) ?? enabled[0])?.id ?? null
  })

  function onItemFocus(item: ChoiceItem) {
    focusedId.value = item.id
  }

  /**
   * Клавиши на кнопке ряда.
   *
   * - ← → (и ↑ ↓) — фокус на соседнюю доступную кнопку, по кругу, «...» — последней. При single кнопка, на которую
   *   перешли, сразу выбирается; сам приход фокуса в ряд (Tab) ничего не выбирает. При множественном выборе стрелки
   *   только двигают фокус.
   * - Пробел — как клик по кнопке.
   * - Backspace, Delete — стереть: при single — значение, иначе — отметку этой кнопки. При required не стирают.
   * - Enter у кнопки не срабатывает: в диалоге он нажимает основную кнопку.
   *
   * item не задан — это «...»: на нём работают только стрелки.
   */
  function onItemKeydown(e: KeyboardEvent, item?: ChoiceItem) {
    const step = { ArrowRight: 1, ArrowDown: 1, ArrowLeft: -1, ArrowUp: -1 }[e.key]
    if (step) {
      e.preventDefault()
      const current = e.currentTarget as HTMLElement
      const buttons = Array.from(current.parentElement?.children ?? []).filter(
        (el): el is HTMLButtonElement => el instanceof HTMLButtonElement && !el.disabled,
      )
      const index = buttons.indexOf(current as HTMLButtonElement)
      const next = buttons[(index + step + buttons.length) % buttons.length]
      if (!next || next === current) return
      next.focus()
      const nextItem = visibleValues.value[Number(next.dataset.index)]
      if (nextItem) {
        focusedId.value = nextItem.id
        if (props.single && !isSelected(nextItem)) onButtonClick(nextItem)
      }
    } else if (!item) {
      // «...»: стрелки — как у кнопок, остальные клавиши — свои
      return
    } else if (e.key === ' ') {
      e.preventDefault()
      onButtonClick(item)
    } else if (e.key === 'Backspace' || e.key === 'Delete') {
      e.preventDefault()
      if (props.required) return
      const cleared = props.single
        ? selected.value.filter((s) => isDisabled(s))
        : selected.value.filter((s) => s.id !== item.id || isDisabled(s))
      if (cleared.length === selected.value.length) return
      selected.value = cleared
      emitChange()
    } else if (e.key === 'Enter') {
      e.preventDefault()
    }
  }

  return {
    selected,
    visibleValues,
    hiddenValues,
    isSelected,
    onButtonClick,
    onMenuPick,
    tabStopId,
    onItemFocus,
    onItemKeydown,
  }
}
