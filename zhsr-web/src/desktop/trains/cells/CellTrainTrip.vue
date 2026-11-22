<template>
  <div v-if="trip">{{ tripRefDatedText(trip) }}<DictBadge :id="status" dict="TripState" class="q-ml-xs" /></div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

import type { TrainRow } from '@/api/trains'
import DictBadge from '@/shared/components/DictBadge.vue'
import type { CellProps } from '@/shared/components/table/types'
import { TripStatus } from '@/shared/constants/systemDicts'
import { tripRefDatedText } from '@/shared/trips/tripRefText'

/**
 * Ближайший рейс поезда: выполняющийся, а если его нет — ближайший в статусе «план»; после рейса — плашка статуса.
 * Рейса нет — ячейка пустая. Справочник TripState готовит страница.
 */
const props = defineProps<CellProps<TrainRow>>()

const trip = computed(() => props.row.trip ?? props.row.nextTrip)
const status = computed(() => (props.row.trip ? TripStatus.IN_PROGRESS : TripStatus.PLANNED))
</script>
