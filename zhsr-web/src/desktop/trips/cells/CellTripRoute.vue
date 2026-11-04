<template>
  <div class="cell-trip-route__name">{{ row.trainNumber }} • {{ row.name }}</div>
  <div class="zs-sub">{{ dictManager.getValue('TransportType', row.transportType) }}</div>
</template>

<script setup lang="ts">
import type { TripRow } from '@/api/trips'
import type { CellProps } from '@/shared/components/table/types'
import { dictManager } from '@/shared/dict/DictManager'

/** Маршрут рейса: номер поезда, название и вид перевозки. */
defineProps<CellProps<TripRow>>()
</script>

<style scoped lang="scss">
.cell-trip-route__name {
  font-weight: 600;
}
</style>
