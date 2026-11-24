<template>
  <span class="hours-input" :style="{ width }">
    <input
      :value="typed"
      class="hours-input__field q-py-none q-px-xs"
      :placeholder="t(placeholder)"
      inputmode="text"
      @focus="startTyping"
      @input="onInput"
      @blur="commit"
      @keydown.enter.prevent="commit"
      @keydown.esc="onEsc"
    />
  </span>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'

import { t } from '@/boot/i18n'
import { formatHoursMinutes, roundHours } from '@/shared/format'

/**
 * Ввод часов — длительности, а не времени суток. Вводятся дробные часы «2,5» («2.5», не больше двух знаков после
 * запятой) или часы и минуты «2:30»; введённое показывается как «2:30». Применяется по Enter или уходу из поля.
 * Ошибка — поле возвращается к прежнему значению. Пустое поле — null. Escape отменяет ввод.
 *
 * Значение — часы числом, не больше двух знаков после запятой: 2:20 — 2.33.
 *
 *   <HoursInput v-model="lnpHours" />
 */
withDefaults(
  defineProps<{
    /** на русском; переводится при показе */
    placeholder?: string
    width?: string
  }>(),
  { placeholder: 'ч:мм', width: '100%' },
)

const model = defineModel<number | null>({ default: null })

// допустимая запись, в том числе недописанная: самое большее четыре цифры часов, две — дроби или минут
const PARTIAL = /^\d{0,4}(?:[.,:]\d{0,2})?$/

// текст поля: пока пользователь печатает, — его текст, иначе — значение
const editing = ref(false)
const draft = ref('')
const valueText = computed(() => (model.value === null ? '' : formatHoursMinutes(model.value)))
const typed = computed(() => (editing.value ? draft.value : valueText.value))

/** Текст → часы; undefined — запись неверна. */
function parse(text: string): number | null | undefined {
  if (!text) return null
  const hm = /^(\d{1,4}):([0-5]\d)$/.exec(text)
  if (hm) return roundHours(Number(hm[1]) + Number(hm[2]) / 60)
  if (/^\d{1,4}(?:[.,]\d{0,2})?$/.test(text)) return Number(text.replace(',', '.'))
  return undefined
}

function startTyping() {
  draft.value = valueText.value
  editing.value = true
}

/** Ввод, который не может стать записью часов, не принимается. */
function onInput(e: Event) {
  const el = e.target as HTMLInputElement
  const text = el.value.trim()
  if (PARTIAL.test(text)) draft.value = text
  else el.value = typed.value
  editing.value = true
}

function commit() {
  if (!editing.value) return
  editing.value = false
  // текст не меняли — значение не трогаем: минуты в тексте округлены
  if (draft.value === valueText.value) return
  const value = parse(draft.value)
  if (value !== undefined) model.value = value
}

/** Escape: пока идёт ввод — только отменяет его и дальше не уходит (не закрывает диалог или страницу). */
function onEsc(e: KeyboardEvent) {
  if (!editing.value || draft.value === valueText.value) return
  e.stopPropagation()
  draft.value = valueText.value
  editing.value = false
}
</script>

<style scoped lang="scss">
.hours-input {
  display: inline-flex;
  align-items: center;
  max-width: 100%;
  height: var(--zs-control-height);
  border: 0.0625rem solid $zs-border-input;
  border-radius: 0.3rem;
  background: #fff;
  font-size: var(--zs-control-font-size);
  cursor: text;

  &:focus-within {
    border-color: $primary;
    box-shadow: 0 0 0 0.0625rem $primary;
  }
}

.hours-input__field {
  flex: 1;
  min-width: 0;
  width: 100%;
  border: 0;
  outline: none;
  background: none;
  color: $zs-text;
  font: inherit;

  &::placeholder {
    color: $zs-text-faint;
  }
}
</style>
