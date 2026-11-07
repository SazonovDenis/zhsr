<template>
  <DictBadge :id="row.tripStatusType" dict="TripState" class="cell-trip-departure__state" />
  <!-- одна дата: пока не отправился — плановая, отправился — фактическая и разница с планом -->
  <div>{{ formatDateTime(row.departedDt ?? row.plannedDt) }}</div>
  <div v-if="row.departedDt" class="zs-sub">{{ delayText(row.plannedDt, row.departedDt) }}</div>
</template>

<script setup lang="ts">
import type { TripRow } from '@/api/trips'
import DictBadge from '@/shared/components/DictBadge.vue'
import type { CellProps } from '@/shared/components/table/types'
import { formatDateTime } from '@/shared/format'
import { delayText } from '@/shared/trips/tripTimes'

/** Отправление: состояние рейса и одна дата — плановая, а после отправления фактическая и разница с планом. */
defineProps<CellProps<TripRow>>()
</script>

<style scoped lang="scss">
.cell-trip-departure__state {
  margin: 0 0 0.3rem -0.5rem;
}
</style>
