<template>
  <div>
    {{ planMain(row) }}<DictBadge v-if="row.planTrip" :id="TripStatus.PLANNED" dict="TripState" class="q-ml-xs" />
  </div>
  <div v-if="planSub(row)" class="zs-sub">{{ planSub(row) }}</div>
</template>

<script setup lang="ts">
import type { StaffRow } from '@/api/staff'
import DictBadge from '@/shared/components/DictBadge.vue'
import type { CellProps } from '@/shared/components/table/types'
import { TripStatus } from '@/shared/constants/systemDicts'
import { planMain, planSub } from '../staffText'

/** Ближайшее назначение (план): рейс и плашка статуса, ниже отправление, должность и вагоны по плану ответственных. */
defineProps<CellProps<StaffRow>>()
</script>
