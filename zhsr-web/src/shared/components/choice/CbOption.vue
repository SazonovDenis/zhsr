<template>
  <span class="cb-option" :title="text">{{ text }}</span>
</template>

<script setup lang="ts">
import { computed } from 'vue'

import type { ChoiceItem } from './choice'
import { displayOf } from './choice'

/**
 * Простая строка выпадающего списка: текст значения в одну строку. Стоит в CbList и в меню «...» по умолчанию, пока
 * вызывающий не задал свой вид строки (слот option). Ширина строки — от ширины поля: в CbList строка не шире
 * --cb-option-max-width (поле, но не уже 25rem, — задаёт CbList), вне CbList — не шире 25rem. Длинный текст обрезается
 * многоточием, полностью он виден в подсказке.
 */
const props = defineProps<{ item: ChoiceItem }>()

const text = computed(() => displayOf(props.item))
</script>

<style scoped lang="scss">
.cb-option {
  display: block;
  max-width: var(--cb-option-max-width, 25rem);
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
</style>
