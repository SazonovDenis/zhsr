<template>
  <span>{{ text }}</span>
</template>

<script setup lang="ts" generic="Row">
import { computed } from 'vue'

import type { CellProps } from './types'

/** Ячейка по умолчанию: значение строкой через column.format, пустое значение — прочерк. */
const props = defineProps<CellProps<Row>>()

const text = computed(() => {
  const format = props.column.format
  if (format) return format(props.value as Row[keyof Row], props.row)
  const value: unknown = props.value
  if (value === null || value === undefined || value === '') return '—'
  if (typeof value === 'string') return value
  if (typeof value === 'number' || typeof value === 'boolean') return String(value)
  return JSON.stringify(value)
})
</script>
