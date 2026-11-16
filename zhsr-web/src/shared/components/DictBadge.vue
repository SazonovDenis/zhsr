<template>
  <StatusBadge :bg="color('bg')" :fg="color('fg')">{{ dictManager.getValue(dict, id) }}</StatusBadge>
</template>

<script setup lang="ts">
import { dictManager } from '@/shared/dict/DictManager'
import type { DictId, DictName } from '@/shared/dict/dicts'
import StatusBadge from './StatusBadge.vue'

/**
 * Плашка значения справочника, например состояния вагона (StatusBadge). Цвета берутся из полей bg и fg записи справочника;
 * если их нет — серая плашка.
 */
const props = defineProps<{ dict: DictName; id: DictId }>()

function color(field: 'bg' | 'fg'): string | undefined {
  const value = dictManager.getRec(props.dict, props.id)?.[field]
  return typeof value === 'string' ? value : undefined
}
</script>
