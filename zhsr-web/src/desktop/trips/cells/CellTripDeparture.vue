<template>
  <DictBadge :id="row.state" dict="TripState" class="cell-trip-departure__state" />
  <div>{{ formatDateTime(row.planned) }}</div>
  <div class="zs-sub">{{ formatDateTime(row.fact) }}</div>
</template>

<script setup lang="ts">
import type { Trip } from '@/api/trips'
import DictBadge from '@/shared/components/DictBadge.vue'
import type { CellProps } from '@/shared/components/table/types'
import { formatDateTime } from '@/shared/format'

/** Отправление: состояние рейса, плановое время и фактическое второй строкой. */
defineProps<CellProps<Trip>>()
</script>

<style scoped lang="scss">
.cell-trip-departure__state {
  margin: 0 0 0.25rem -0.5rem;
}
</style>
