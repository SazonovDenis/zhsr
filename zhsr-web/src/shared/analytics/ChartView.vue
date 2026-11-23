<template>
  <div class="chart-view">
    <slot name="tools" />
    <ErrorBox v-if="chart.error.value" :error="chart.error.value" :retry="chart.load" />
    <div v-else class="chart-view__body">
      <div
        v-if="chart.data.value"
        class="chart-view__content"
        :class="{ 'chart-view__content--loading': chart.active.value }"
      >
        <div v-if="empty" class="chart-view__empty">{{ empty }}</div>
        <slot v-else :data="chart.data.value" />
      </div>
      <!-- первая загрузка — спиннер один, повторная — поверх бледной диаграммы -->
      <div v-if="chart.active.value" :class="{ 'chart-view__spinner': chart.data.value }">
        <LoadSpinner />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts" generic="T">
import ErrorBox from '@/shared/components/ErrorBox.vue'
import LoadSpinner from '@/shared/components/LoadSpinner.vue'
import type { ChartState } from './useChart'

/**
 * Общий предок диаграмм раздела «Аналитика»: показывает состояние диаграммы (useChart) — спиннер, ошибку с повтором,
 * сообщение empty (переведённое; пусто — данные есть) или саму диаграмму (слот default, получает data). При повторной
 * загрузке прежняя диаграмма остаётся, бледная, спиннер — по её центру; при ошибке диаграммы нет.
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

.chart-view__body,
.chart-view__content {
  display: flex;
  flex: 1 0 auto;
  flex-direction: column;
}

.chart-view__body {
  position: relative;
}

.chart-view__content {
  transition: opacity 0.2s;

  &--loading {
    opacity: 0.5;
  }
}

.chart-view__spinner {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  pointer-events: none;
}

.chart-view__empty {
  color: $zs-text-muted;
}
</style>
