<template>
  <div>
    {{ factMain(row) }}<DictBadge v-if="row.factTrip" :id="TripStatus.IN_PROGRESS" dict="TripState" class="q-ml-xs" />
  </div>
  <div v-if="factSub(row)" class="zs-sub">{{ factSub(row) }}</div>
</template>

<script setup lang="ts">
import type { StaffRow } from '@/api/staff'
import DictBadge from '@/shared/components/DictBadge.vue'
import type { CellProps } from '@/shared/components/table/types'
import { TripStatus } from '@/shared/constants/systemDicts'
import { factMain, factSub } from '../staffText'

/**
 * Где сотрудник сейчас (факт): в рейсе — рейс и плашка статуса, ниже должность и вагоны; иначе станция, ниже депо и
 * вагоны.
 */
defineProps<CellProps<StaffRow>>()
</script>
