<template>
  <DictBadge :id="row.tripStatusType" dict="TripState" class="cell-trip-departure__state" />
  <div>{{ formatDateTime(row.plannedDt) }}</div>
  <div class="zs-sub">{{ formatDateTime(row.departedDt) }}</div>
</template>

<script setup lang="ts">
import type { TripRow } from '@/api/trips'
import DictBadge from '@/shared/components/DictBadge.vue'
import type { CellProps } from '@/shared/components/table/types'
import { formatDateTime } from '@/shared/format'

/** Отправление: состояние рейса, плановое время и фактическое второй строкой. */
defineProps<CellProps<TripRow>>()
</script>

<style scoped lang="scss">
.cell-trip-departure__state {
  margin: 0 0 0.25rem -0.5rem;
}
</style>
