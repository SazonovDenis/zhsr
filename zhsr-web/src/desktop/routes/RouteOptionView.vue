<template>
  <!-- line — в одну строку: номер и сообщение, справа время в пути и длина; без вида маршрута и станций -->
  <div v-if="line" class="route-option">
    <span class="route-option__name route-option__line-name">№ {{ route.trainNumber }} • {{ route.name }}</span>
    <span class="route-option__totals route-option__totals--line">
      <span>{{ duration }}</span>
      <span v-if="km">{{ km }}</span>
    </span>
  </div>
  <div v-else class="route-option">
    <div class="route-option__main">
      <div class="route-option__title">
        <span class="route-option__name">№ {{ route.trainNumber }} • {{ route.name }}</span>
        <span class="route-option__kind q-py-none q-px-sm">{{
          dictManager.getValue('RouteKind', route.routeKind)
        }}</span>
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
import { formatHm, formatHoursMin } from './duration'

/**
 * Маршрут одной строкой по макету: слева номер поезда, сообщение и вид маршрута, под ними цепочка остановок; справа
 * время в пути и длина маршрута. Одинаково и в списке, и в поле выбора (RouteOption). line — одной строкой: номер и
 * сообщение, время в пути и длина, без вида маршрута и станций: для выбранного значения в поле. words — время в пути словами
 * («23 ч 34 мин»): для строки выпадающего списка.
 */
const props = withDefaults(defineProps<{ route: RouteOption; line?: boolean; words?: boolean }>(), {
  line: false,
  words: false,
})

// цепочка остановок; станции следования (без остановки) не показываются
const path = computed(() => props.route.stops.map((station) => dictManager.getValue('Station', station)).join(' → '))

// время в пути — атрибут маршрута (route.duration); words — словами
const duration = computed(() => (props.words ? formatHoursMin : formatHm)(props.route.duration))

const km = computed(() => (props.route.km > 0 ? t('{km} км', { km: props.route.km }) : ''))
</script>

<style scoped lang="scss">
.route-option {
  display: flex;
  align-items: center;
  gap: 0.8rem;
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
  font-size: 0.8rem;
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
  border-radius: 0.3rem;
  background: $zs-bg-button-soft;
  color: $zs-text-muted;
  font-size: 0.7rem;
}

// цепочка остановок бывает длинной: переносится по ширине, а не растягивает выпадающий список
.route-option__path {
  max-width: 37.3rem;
  color: $zs-text-muted;
  font-size: 0.8rem;
  line-height: 1.35;
}

.route-option__line-name {
  flex: 1;
  min-width: 0;
}

.route-option__totals {
  display: flex;
  flex: none;
  flex-direction: column;
  align-items: flex-end;
  color: $zs-text-muted;
  font-size: 0.8rem;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

// в одну строку: время и длина рядом
.route-option__totals--line {
  flex-direction: row;
  gap: 0.8rem;
}
</style>
