<template>
  <div class="analytics-grid">
    <div
      v-for="chart in visibleCharts()"
      :key="chart.id"
      role="button"
      tabindex="0"
      class="analytics-cell zs-focusable q-pa-md"
      @click="emit('open', chart.id)"
      @keydown.enter.self="emit('open', chart.id)"
    >
      <div class="analytics-cell__title q-mb-sm">{{ t(chart.title) }}</div>
      <div class="analytics-cell__body">
        <component :is="chart.component" :detailed="false" />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { t } from '@/boot/i18n'
import { visibleCharts } from './charts'

/**
 * Сетка диаграмм дашборда «Аналитика», общая для десктопа и телефона. В ячейке — заголовок и тело диаграммы без ФИО;
 * нажатие — событие open с id диаграммы. Число ячеек в строке зависит от ширины (на телефоне — одна), строки делят
 * высоту страницы, при нехватке высоты прокручивается страница.
 */
const emit = defineEmits<{ open: [id: string] }>()
</script>

<style scoped lang="scss">
.analytics-grid {
  display: flex;
  // не сжимается: диаграммы не помещаются — прокручивается страница
  flex: 1 0 auto;
  flex-wrap: wrap;
  gap: 1rem;
}

.analytics-cell {
  display: flex;
  // не больше трёх ячеек в строке; не уже 22rem, а на узком экране — на всю ширину
  flex: 1 1 min(100%, max(22rem, 30%));
  flex-direction: column;
  box-sizing: border-box;
  min-width: 0;
  // три ячейки помещаются (3 × 22rem и два промежутка) — не шире трети строки, иначе ширина не ограничена
  max-width: max((100% - 2rem) / 3, (68rem - 100%) * 1000);
  min-height: 22rem;
  border: 0.1rem solid $zs-border;
  border-radius: 0.5rem;
  background: $zs-bg-panel;
  cursor: pointer;

  &:hover {
    border-color: $zs-selected-border;
  }
}

.analytics-cell__title {
  font-size: 0.9rem;
  font-weight: 600;
  line-height: 1.3;
}

.analytics-cell__body {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
}
</style>
