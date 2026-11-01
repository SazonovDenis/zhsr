<template>
  <div class="route-option">
    <div class="route-option__main">
      <div class="route-option__title">
        <span class="route-option__name">№ {{ route.train }} • {{ route.name }}</span>
        <span class="route-option__kind">{{ dictManager.getValue('RouteKind', route.kind) }}</span>
      </div>
      <div class="route-option__path">{{ path }}</div>
    </div>
    <div class="route-option__totals">
      <span>{{ duration }}</span>
      <span>{{ km }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'

import type { Route } from '@/api/routes'
import { t } from '@/boot/i18n'
import { dictManager } from '@/shared/dict/DictManager'
import { formatHm, totalMinutes } from './duration'

/**
 * Маршрут одной строкой по макету: слева номер поезда, сообщение и вид маршрута, под ними цепочка остановок; справа
 * время в пути и длина маршрута. Одинаково и в списке, и в поле выбора (RouteChoice).
 */
const props = defineProps<{ route: Route }>()

// цепочка остановок; станции следования (без остановки) не показываются
const path = computed(() =>
  props.route.points
    .filter((p) => p.stop)
    .map((p) => dictManager.getValue('Station', p.station))
    .join(' → '),
)

const duration = computed(() =>
  props.route.points.length > 0 ? formatHm(totalMinutes(props.route.points)) : formatHm(props.route.durationHours * 60),
)

const km = computed(() => {
  const last = props.route.points.at(-1)
  return last ? t('{km} км', { km: last.km }) : ''
})

onMounted(() => dictManager.prepareDict('Station'))
</script>

<style scoped lang="scss">
.route-option {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  min-width: 0;
}

// в выпадающем списке строка шире простой (CbOption): на ней больше сведений
.route-option--list {
  min-width: 30rem;
}

.route-option__main {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 0.0625rem;
  min-width: 0;
}

.route-option__name {
  overflow: hidden;
  font-size: 0.84375rem;
  font-weight: 600;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.route-option__title {
  display: flex;
  align-items: baseline;
  gap: 0.5rem;
  min-width: 0;
}

.route-option__kind {
  flex: none;
  padding: 0 0.375rem;
  border-radius: 0.25rem;
  background: $zs-bg-button-soft;
  color: $zs-text-muted;
  font-size: 0.6875rem;
}

// цепочка остановок бывает длинной: переносится по ширине, а не растягивает выпадающий список
.route-option__path {
  max-width: 37.3rem;
  color: $zs-text-muted;
  font-size: 0.75rem;
  line-height: 1.35;
}

.route-option__totals {
  display: flex;
  flex: none;
  flex-direction: column;
  align-items: flex-end;
  color: $zs-text-muted;
  font-size: 0.78125rem;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}
</style>
