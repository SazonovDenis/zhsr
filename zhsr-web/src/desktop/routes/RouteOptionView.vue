<template>
  <div class="route-option">
    <div class="route-option__main">
      <div class="route-option__title">
        <span class="route-option__name">№ {{ route.trainNumber }} • {{ route.name }}</span>
        <span class="route-option__kind">{{ dictManager.getValue('RouteKind', route.routeKind) }}</span>
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
import { computed } from 'vue'

import type { RouteOption } from '@/api/routes'
import { t } from '@/boot/i18n'
import { dictManager } from '@/shared/dict/DictManager'
import { formatHm } from './duration'

/**
 * Маршрут одной строкой по макету: слева номер поезда, сообщение и вид маршрута, под ними цепочка остановок; справа
 * время в пути и длина маршрута. Одинаково и в списке, и в поле выбора (RouteOption).
 */
const props = defineProps<{ route: RouteOption }>()

// цепочка остановок; станции следования (без остановки) не показываются
const path = computed(() => props.route.stops.map((station) => dictManager.getValue('Station', station)).join(' → '))

// время в пути — атрибут маршрута (route.duration)
const duration = computed(() => formatHm(props.route.duration))

const km = computed(() => (props.route.km > 0 ? t('{km} км', { km: props.route.km }) : ''))
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
