<template>
  <DictBadge :id="row.tripStatusType" dict="TripState" class="cell-trip-departure__state" />
  <!-- одна дата: пока не отправился — плановая, отправился — фактическая и разница с планом; в пути — сколько уже едет -->
  <div v-if="inProgress && row.departedDt">{{ sinceText(row.departedDt, nowMinute) }}</div>
  <div v-else>{{ formatDateTime(row.departedDt ?? row.plannedDt) }}</div>
  <div v-if="row.departedDt" class="zs-sub">{{ delayText(row.plannedDt, row.departedDt) }}</div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

import type { TripRow } from '@/api/trips'
import DictBadge from '@/shared/components/DictBadge.vue'
import type { CellProps } from '@/shared/components/table/types'
import { TripStatus } from '@/shared/constants/systemDicts'
import { formatDateTime } from '@/shared/format'
import { nowMinute, sinceText } from '@/shared/since'
import { delayText } from '@/shared/trips/tripTimes'

/**
 * Отправление: состояние рейса и одна дата — плановая, а после отправления фактическая и разница с планом; рейс в пути —
 * «с 25.10 01:05, 8 час 25 мин».
 */
const props = defineProps<CellProps<TripRow>>()

const inProgress = computed(() => props.row.tripStatusType === TripStatus.IN_PROGRESS)
</script>

<style scoped lang="scss">
.cell-trip-departure__state {
  margin: 0 0 0.3rem -0.5rem;
}
</style>
