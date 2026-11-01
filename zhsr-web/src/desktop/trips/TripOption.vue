<template>
  <div class="trip-option">
    <span class="trip-option__code zs-mono">{{ trip.train }}</span>
    <div class="trip-option__main">
      <div class="trip-option__label">
        {{ trip.routeName }} • {{ t('рейс от {date}', { date: formatDateTime(trip.planned) }) }}
      </div>
      <div class="trip-option__sub">{{ sub }}</div>
    </div>
    <DictBadge :id="trip.state" dict="TripState" class="trip-option__state" />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'

import type { Route } from '@/api/routes'
import type { Trip } from '@/api/trips'
import { t } from '@/boot/i18n'
import DictBadge from '@/shared/components/DictBadge.vue'
import { dictManager } from '@/shared/dict/DictManager'
import { formatDateTime } from '@/shared/format'

/**
 * Рейс одной строкой по макету (фильтр рейса в списке вагонов): слева номер поезда моноширинным шрифтом; посередине
 * маршрут и плановое отправление, под ними остановки маршрута, его длина, вид маршрута и время в пути; справа
 * состояние рейса плашкой, как в списке рейсов (DictBadge). route — маршрут рейса; не задан — второй строкой только
 * время в пути.
 */
const props = withDefaults(defineProps<{ trip: Trip; route?: Route | null }>(), { route: null })

// «Астана → Караганды → Алматы • 1 228 км • регулярный • 19 ч»
const sub = computed(() => {
  const hours = t('{n} ч', { n: props.trip.durationHours })
  const route = props.route
  if (!route) return hours
  const stops = route.points
    .filter((p) => p.stop)
    .map((p) => dictManager.getValue('Station', p.station))
    .join(' → ')
  const km = t('{km} км', { km: route.points.at(-1)?.km ?? 0 })
  const kind = dictManager.getValue('RouteKind', route.kind).toLowerCase()
  return [stops, km, kind, hours].join(' • ')
})

onMounted(() => dictManager.prepareDicts('TripState', 'RouteKind', 'Station'))
</script>

<style scoped lang="scss">
.trip-option {
  display: flex;
  align-items: center;
  gap: 0.625rem;
  min-width: 0;
}

// в выпадающем списке — ширина окна из макета
.trip-option--list {
  min-width: 42.5rem;
}

.trip-option__code {
  flex: none;
  min-width: 3rem;
  font-size: 0.8125rem;
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
  font-size: 0.84375rem;
  font-weight: 600;
  overflow-wrap: anywhere;
}

.trip-option__sub {
  color: $zs-text-muted;
  font-size: 0.75rem;
}

.trip-option__state {
  flex: none;
}
</style>
