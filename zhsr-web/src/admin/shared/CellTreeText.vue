<template>
  <div
    class="cell-tree"
    :class="{ 'cell-tree--section': row.section }"
    :style="{ paddingLeft: `${row.depth * 1.2}rem` }"
  >
    {{ row.text }}
  </div>
</template>

<script setup lang="ts">
import type { CellProps } from '@/shared/components/table/types'

/** Текст пункта шаблона с отступом по глубине в дереве; раздел — полужирным. */
defineProps<CellProps<{ depth: number; section: boolean; text: string }>>()
</script>

<style scoped lang="scss">
.cell-tree--section {
  font-weight: 600;
}
</style>
