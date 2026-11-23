<template>
  <div class="chart-view">
    <slot name="tools" />
    <LoadSpinner v-if="chart.active.value" />
    <ErrorBox v-else-if="chart.error.value" :error="chart.error.value" :retry="chart.load" />
    <template v-else-if="chart.data.value">
      <div v-if="empty" class="chart-view__empty">{{ empty }}</div>
      <slot v-else :data="chart.data.value" />
    </template>
  </div>
</template>

<script setup lang="ts" generic="T">
import ErrorBox from '@/shared/components/ErrorBox.vue'
import LoadSpinner from '@/shared/components/LoadSpinner.vue'
import type { ChartState } from './useChart'

/**
 * Общий предок диаграмм раздела «Аналитика»: показывает состояние диаграммы (useChart) — спиннер, ошибку с повтором,
 * сообщение empty (переведённое; пусто — данные есть) или саму диаграмму (слот default, получает data).
 *
 * Слот tools — переключатели над диаграммой, которые стоят на месте и во время загрузки.
 */
defineProps<{ chart: ChartState<T>; empty?: string }>()

defineSlots<{ tools?: () => unknown; default: (props: { data: NonNullable<T> }) => unknown }>()
</script>

<style scoped lang="scss">
.chart-view {
  display: flex;
  // не сжимается: диаграмма выше экрана — прокручивается страница
  flex: 1 0 auto;
  flex-direction: column;
}

.chart-view__empty {
  color: $zs-text-muted;
}
</style>
