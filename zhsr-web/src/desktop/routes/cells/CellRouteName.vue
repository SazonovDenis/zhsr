<template>
  <div class="cell-route-name">{{ row.trainNumber }} • {{ row.name }}</div>
  <div class="zs-sub">{{ path }}</div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

import type { RouteRow } from '@/api/routes'
import type { CellProps } from '@/shared/components/table/types'
import { dictManager } from '@/shared/dict/DictManager'

/** Маршрут: номер поезда, сообщение и остановки через стрелку. */
const props = defineProps<CellProps<RouteRow>>()

const path = computed(() => props.row.stops.map((station) => dictManager.getValue('Station', station)).join(' → '))
</script>

<style scoped lang="scss">
.cell-route-name {
  font-weight: 600;
}
</style>
