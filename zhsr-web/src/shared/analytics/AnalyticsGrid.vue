<template>
  <div class="analytics-grid">
    <div
      v-for="chart in visibleCharts()"
      :key="chart.id"
      role="button"
      tabindex="0"
      class="analytics-cell zs-focusable q-px-md q-pb-md"
      @click="emit('open', chart.id)"
      @keydown.enter.self="emit('open', chart.id)"
    >
      <div class="analytics-cell__title">
        <span>{{ t(chart.title) }}</span>
        <span class="analytics-cell__more q-ml-auto">
          <span class="analytics-cell__more-text q-mr-xs">{{ t('Подробнее') }}</span>
          <q-icon name="open_in_new" size="1rem" class="analytics-cell__more-icon" />
        </span>
      </div>
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
 * высоту страницы, при нехватке высоты прокручивается страница. У правого края заголовка значок «открыть», при наведении
 * на заголовок перед ним видно «Подробнее».
 *
 * Диаграмма может обрабатывать клики внутри миниатюры сама, например как в раскрытом виде: такой клик она
 * останавливает (stopPropagation), и ячейка его не получает.
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
  // отступы заголовка чуть меньше ступеней Quasar
  padding-top: 0.7rem;
  border: 0.1rem solid $zs-border;
  border-radius: 0.5rem;
  background: $zs-bg-panel;
  cursor: pointer;

  &:hover {
    border-color: $zs-selected-border;
  }
}

.analytics-cell__title {
  display: flex;
  align-items: baseline;
  gap: 0.5rem;
  margin-bottom: 0.3rem;
  font-size: 0.9rem;
  font-weight: 600;
  line-height: 1.3;
}

// значок виден всегда, слово — при наведении на заголовок; место под слово занято всегда: его появление не переносит
// заголовок и не сдвигает диаграмму
.analytics-cell__more {
  color: $zs-text-muted;
  font-weight: 400;
  white-space: nowrap;
}

// по строке текста значок стоит низко — приподнят
.analytics-cell__more-icon {
  position: relative;
  top: -0.1rem;
}

.analytics-cell__more-text {
  visibility: hidden;
  font-size: 0.8rem;
}

.analytics-cell__title:hover .analytics-cell__more-text {
  visibility: visible;
}

.analytics-cell__body {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
}
</style>
