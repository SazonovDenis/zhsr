<template>
  <div class="cell-route-name">{{ row.train }} • {{ row.name }}</div>
  <div class="zs-sub">{{ path }}</div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

import type { Route } from '@/api/routes'
import type { CellProps } from '@/shared/components/table/types'
import { dictManager } from '@/shared/dict/DictManager'

/** Маршрут: номер поезда, сообщение и остановки через стрелку. */
const props = defineProps<CellProps<Route>>()

const path = computed(() =>
  props.row.points
    .filter((p) => p.stop)
    .map((p) => dictManager.getValue('Station', p.station))
    .join(' → '),
)
</script>

<style scoped lang="scss">
.cell-route-name {
  font-weight: 600;
}
</style>
