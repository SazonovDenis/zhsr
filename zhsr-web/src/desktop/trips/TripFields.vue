<template>
  <SidePanelFields>
    <span>{{ t('Маршрут') }}</span>
    <span>
      <SoftRouteLink
        text="{number}"
        :params="{ number: trip.trainNumber }"
        :to="{ name: 'route-edit', params: { id: trip.route } }"
      />
      • {{ trip.name }}
    </span>
    <span>{{ t('Поезд') }}</span>
    <span>
      <template v-if="trip.train">{{ trip.train.name }}</template>
      <template v-else>{{ t('не назначен') }}</template>
    </span>
    <span>{{ t('Вид маршрута') }}</span>
    <span>{{ dictManager.getValue('TransportType', trip.transportType) }}</span>
    <span>{{ t('Время в пути') }}</span>
    <span>{{ formatHm(trip.duration) }}</span>
    <TripTimes
      :planned-dt="trip.plannedDt"
      :departed-dt="trip.departedDt"
      :arrived-dt="trip.arrivedDt"
      :duration="trip.duration"
    />
    <span>{{ t('Состояние') }}</span>
    <span><DictBadge :id="trip.tripStatusType" dict="TripState" /></span>
  </SidePanelFields>
</template>

<script setup lang="ts">
import type { TrainRef } from '@/api/trains'
import { t } from '@/boot/i18n'
import { formatHm } from '@/desktop/routes/duration'
import DictBadge from '@/shared/components/DictBadge.vue'
import SoftRouteLink from '@/shared/components/form/SoftRouteLink.vue'
import { dictManager } from '@/shared/dict/DictManager'
import SidePanelFields from '../frame/SidePanelFields.vue'
import TripTimes from './TripTimes.vue'

/**
 * Атрибуты рейса в правой панели — одни и те же в списке рейсов и в формировании рейса: маршрут (номер поезда
 * маршрута ссылкой на редактор маршрута, название текстом), поезд, вид маршрута, время в пути, времена, состояние.
 */
defineProps<{
  trip: {
    route: number
    trainNumber: string
    name: string
    train: TrainRef | null
    transportType: number
    duration: number
    plannedDt: string
    departedDt: string | null
    arrivedDt: string | null
    tripStatusType: number
  }
}>()
</script>
