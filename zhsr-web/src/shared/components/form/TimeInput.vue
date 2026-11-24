<template>
  <span class="time-input" :style="{ width }">
    <input
      :value="typed"
      class="time-input__field zs-focusable q-py-none q-px-sm"
      :placeholder="t(placeholder)"
      inputmode="numeric"
      @focus="startTyping"
      @input="onTypedInput"
      @blur="commitTyped"
      @keydown.enter.prevent="commitTyped"
      @keydown.esc="onEsc"
    />
  </span>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'

import { t } from '@/boot/i18n'

/**
 * Поле ввода времени «чч:мм» с маской, как у даты в DatePicker: «1000» и «10:00» равноценны — двоеточие встаёт само.
 * Набранное двоеточие закрывает часы из одной цифры: «9:» — «09:». Время с двумя цифрами минут применяется сразу;
 * остальное — по Enter или уходу из поля: «9» — 09:00. Ошибка — поле возвращается к прежнему значению. Escape
 * отменяет ввод.
 *
 * Значение — минуты от полуночи. Значение 1440 и больше (следующие сутки) показывается временем суток: 1615 — «02:55».
 * Введённое время — всегда 0…1439; сутки определяет владелец. Время суток не изменилось — значение прежнее.
 *
 *   <TimeInput v-model="departureMin" :nullable="false" />
 */
const props = withDefaults(
  defineProps<{
    /** на русском; переводится при показе */
    placeholder?: string
    width?: string
    /** пустое поле очищает время; иначе пустое поле возвращается к прежнему значению */
    nullable?: boolean
  }>(),
  { placeholder: 'чч:мм', width: '100%', nullable: true },
)

const model = defineModel<number | null>({ default: null })

/** Минуты от полуночи → время суток «чч:мм». */
function textOf(minutes: number | null): string {
  if (minutes == null) return ''
  return `${String(Math.floor(minutes / 60) % 24).padStart(2, '0')}:${String(minutes % 60).padStart(2, '0')}`
}

// текст поля: пока пользователь печатает, — его текст, иначе — значение
const editing = ref(false)
const draftText = ref('')
const typed = computed(() => (editing.value ? draftText.value : textOf(model.value)))

// группы записи: часы и минуты, в каждой самое большее две цифры
const GROUP_MAX = 2
const LAST = 1

interface Groups {
  /** цифры каждой набранной группы, без дополнения нулём */
  digits: string[]
  /** группа, которую сейчас набирают; группы до неё закрыты */
  current: number
}

/**
 * Разбор набранного текста по группам — как в DatePicker. Цифра идёт в текущую группу; группа заполнена — в
 * следующую. Разделитель (любой нецифровой знак) после заполненных часов переводит к минутам. Часы из одной цифры
 * закрывает только разделитель, набранный сейчас (typed — его позиция в тексте); уже стоявший разделитель после
 * неполной группы пропускается, и цифры сдвигаются, как в обычной маске. caret — позиция в тексте; возвращается, в
 * какой группе и после скольких её цифр она оказалась.
 */
function parseGroups(
  raw: string,
  caret: number,
  typed: (i: number) => boolean,
): Groups & { caretGroup: number; caretLen: number } {
  const digits = ['']
  let current = 0
  let caretGroup = 0
  let caretLen = 0
  for (let i = 0; i < raw.length; i++) {
    if (i === caret) [caretGroup, caretLen] = [current, digits[current]!.length]
    const ch = raw[i]!
    if (/\d/.test(ch)) {
      if (digits[current]!.length === GROUP_MAX) {
        if (current === LAST) continue
        digits[++current] = ''
      }
      digits[current] += ch
    } else {
      const len = digits[current]!.length
      if (current < LAST && (len === GROUP_MAX || (len > 0 && typed(i)))) digits[++current] = ''
    }
  }
  if (caret >= raw.length) [caretGroup, caretLen] = [current, digits[current]!.length]
  return { digits, current, caretGroup, caretLen }
}

/** Закрытая группа из одной цифры дополняется нулём: «9:» → «09:». */
function groupText(groups: Groups, i: number): string {
  const d = groups.digits[i]!
  return i < groups.current && d.length === 1 ? `0${d}` : d
}

/** Группы → текст: «10:0», «09:»; двоеточие после закрытых часов видно сразу. */
function formatGroups(groups: Groups): string {
  let text = ''
  for (let i = 0; i <= groups.current; i++) text += (i > 0 ? ':' : '') + groupText(groups, i)
  return text
}

/** Ввод в поле: разделитель встаёт сам; курсор остаётся после той же цифры, что и до разбора. */
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

  if (groups.current === LAST && groups.digits[LAST]!.length === GROUP_MAX) applyText(text)
}

/**
 * Разобрать текст поля и применить: «10:00», «9:05», «9» — 09:00. Пусто — время очищается, если оно может быть
 * пустым. Ошибка — значение не меняется. Возвращает, удалось ли применить.
 */
function applyText(text: string): boolean {
  if (!text) {
    if (props.nullable) model.value = null
    return props.nullable
  }
  const m = /^(\d{1,2})(?::(\d{2}))?$/.exec(text)
  if (!m) return false
  const hours = Number(m[1])
  const minutes = Number(m[2] ?? 0)
  if (hours > 23 || minutes > 59) return false
  const value = hours * 60 + minutes
  if (model.value == null || value !== model.value % (24 * 60)) model.value = value
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

/** Escape в поле: пока идёт ввод — только отменяет его и дальше не уходит (не закрывает диалог или страницу). */
function onEsc(e: KeyboardEvent) {
  if (!editing.value || draftText.value === textOf(model.value)) return
  e.stopPropagation()
  editing.value = false
}
</script>

<style scoped lang="scss">
.time-input {
  display: inline-flex;
  max-width: 100%;
}

.time-input__field {
  width: 100%;
  height: var(--zs-control-height);
  border: 0.0625rem solid $zs-border-input;
  border-radius: 0.3rem;
  background: #fff;
  color: $zs-text;
  font: inherit;
  font-size: var(--zs-control-font-size);

  &::placeholder {
    color: $zs-text-faint;
  }

  &:focus {
    border-color: $primary;
    outline: none;
    box-shadow: 0 0 0 0.0625rem $primary;
  }
}
</style>
