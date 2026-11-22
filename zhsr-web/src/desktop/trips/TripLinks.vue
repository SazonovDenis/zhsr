<template>
  <!-- рейсы ссылкой на состав поезда рейса и статус рейса плашкой -->
  <div v-if="trips.length > 0" class="trip-links">
    <div class="trip-links__title">{{ t(title) }}</div>
    <div v-for="tr in trips" :key="tr.trip.id" class="trip-links__trip">
      <button type="button" class="trip-links__link q-pa-none" @click="open(tr.trip.id)">
        {{ tripRefDatedText(tr.trip) }}
      </button>
      <DictBadge :id="tr.tripStatusType" dict="TripState" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'

import type { TripRef } from '@/api/wagons'
import { t } from '@/boot/i18n'
import DictBadge from '@/shared/components/DictBadge.vue'
import { tripRefDatedText } from '@/shared/trips/tripRefText'
import { openTripTrain } from './openTripTrain'

/**
 * Блок «Рейсы» правой панели — один для вагона и поезда: рейс ссылкой на экран поезда с этим рейсом (состав на
 * отправление рейса) и его статус плашкой.
 * title — подпись блока на русском, по умолчанию «Рейсы». Рейсов нет — блока нет. Справочник TripState готовит страница.
 */
withDefaults(defineProps<{ trips: { trip: TripRef; tripStatusType: number }[]; title?: string }>(), {
  title: 'Рейсы',
})

const router = useRouter()

function open(id: number) {
  void openTripTrain(router, id)
}
</script>

<style scoped lang="scss">
.trip-links {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.trip-links__title {
  color: $zs-text-muted;
  font-size: 0.9rem;
  font-weight: 600;
}

.trip-links__trip {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.trip-links__link {
  border: 0;
  background: none;
  color: $primary;
  font: inherit;
  text-align: left;
  cursor: pointer;
}
</style>
