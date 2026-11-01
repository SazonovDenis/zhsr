<template>
  <span class="zs-badge" :style="{ background: color('bg'), color: color('fg') }">{{
    dictManager.getValue(dict, id)
  }}</span>
</template>

<script setup lang="ts">
import { dictManager } from '@/shared/dict/DictManager'
import type { DictId, DictName } from '@/shared/dict/dicts'

/**
 * Плашка значения справочника, например состояния вагона. Цвета берутся из полей bg и fg записи справочника; если их
 * нет — серая плашка.
 */
const props = defineProps<{ dict: DictName; id: DictId }>()

function color(field: 'bg' | 'fg'): string | undefined {
  const value = dictManager.getRec(props.dict, props.id)?.[field]
  if (typeof value === 'string') return value
  return field === 'bg' ? '#eef0f3' : '#62666d'
}
</script>
