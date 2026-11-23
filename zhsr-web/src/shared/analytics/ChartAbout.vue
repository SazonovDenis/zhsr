<template>
  <div class="chart-about">
    <section v-if="find">
      <h3 class="chart-about__head q-mt-none q-mb-xs">{{ t('Закономерность') }}</h3>
      <p class="q-ma-none">{{ find }}</p>
    </section>
    <section>
      <h3 class="chart-about__head q-mt-none q-mb-xs">{{ t('Цель') }}</h3>
      <p class="q-ma-none">{{ t(chart.goal) }}</p>
    </section>
    <section>
      <h3 class="chart-about__head q-mt-none q-mb-xs">{{ t('Решает') }}</h3>
      <p class="q-ma-none">{{ t(chart.problem) }}</p>
    </section>
    <section v-if="chart.how">
      <h3 class="chart-about__head q-mt-none q-mb-xs">{{ t('Как читать') }}</h3>
      <p class="q-ma-none">{{ t(chart.how) }}</p>
    </section>
  </div>
</template>

<script setup lang="ts">
import { t } from '@/boot/i18n'
import type { ChartDef } from './charts'

/** Сведения о диаграмме: закономерность (find — от диаграммы, после загрузки данных), цель, решаемая проблема, как читать. */
defineProps<{ chart: ChartDef; find: string }>()
</script>

<style scoped lang="scss">
.chart-about {
  display: flex;
  flex-direction: column;
  gap: 1.2rem;
  font-size: 0.9rem;
  line-height: 1.4;
}

.chart-about__head {
  color: $zs-text-muted;
  font-size: 0.8rem;
  font-weight: 600;
  // h3 Quasar задаёт крупную высоту строки — отсюда большой отступ под заголовком
  line-height: 1.3;
  letter-spacing: 0;
  text-transform: uppercase;
}
</style>
