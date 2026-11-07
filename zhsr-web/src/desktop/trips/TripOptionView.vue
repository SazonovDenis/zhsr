<template>
  <div class="trip-option">
    <span class="trip-option__code zs-mono">{{ trip.trainNumber }}</span>
    <div class="trip-option__main">
      <div class="trip-option__label">
        {{ trip.name }} • {{ t('рейс от {date}', { date: formatDateTime(trip.plannedDt) }) }}
      </div>
      <div class="trip-option__sub">{{ sub }}</div>
    </div>
    <DictBadge :id="trip.tripStatusType" dict="TripState" class="trip-option__state" />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

import type { TripOption } from '@/api/trips'
import { t } from '@/boot/i18n'
import { formatHm } from '@/desktop/routes/duration'
import DictBadge from '@/shared/components/DictBadge.vue'
import { dictManager } from '@/shared/dict/DictManager'
import { formatDateTime } from '@/shared/format'

/**
 * Рейс одной строкой по макету (фильтр «Рейс»): слева номер поезда моноширинным шрифтом; посередине маршрут и плановое
 * отправление, под ними остановки маршрута, его длина, вид маршрута и время в пути; справа состояние рейса плашкой, как
 * в списке рейсов (DictBadge). Станции — справочник Station: его загружает владелец выпадающего списка.
 */
const props = defineProps<{ trip: TripOption }>()

// «Астана → Караганды → Алматы • 1 228 км • регулярный • 19 ч»
const sub = computed(() => {
  const stops = props.trip.stops.map((station) => dictManager.getValue('Station', station)).join(' → ')
  const km = t('{km} км', { km: props.trip.km })
  const kind = dictManager.getValue('RouteKind', props.trip.routeKind).toLowerCase()
  const hours = formatHm(props.trip.duration)
  return [stops, km, kind, hours].filter((part) => part).join(' • ')
})
</script>

<style scoped lang="scss">
.trip-option {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  min-width: 0;
}

// в выпадающем списке — ширина окна из макета
.trip-option--list {
  min-width: 42.5rem;
}

.trip-option__code {
  flex: none;
  min-width: 3rem;
  font-size: 0.8rem;
  font-weight: 600;
}

.trip-option__main {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 0.0625rem;
  min-width: 0;
}

.trip-option__label {
  font-size: 0.8rem;
  font-weight: 600;
  overflow-wrap: anywhere;
}

.trip-option__sub {
  color: $zs-text-muted;
  font-size: 0.8rem;
}

.trip-option__state {
  flex: none;
}
</style>
