<template>
  <PwaPage white :back="goBack" :title="chart ? t(chart.title) : t('Диаграмма не найдена')">
    <template v-if="chart">
      <div class="chart-scroll">
        <div class="chart-scroll__body" :style="{ minWidth: chart.minWidth }">
          <component :is="chart.component" detailed @find="find = $event" />
        </div>
      </div>
      <ChartAbout :chart="chart" :find="find" />
    </template>
  </PwaPage>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'

import { t } from '@/boot/i18n'
import ChartAbout from '@/shared/analytics/ChartAbout.vue'
import { chartById } from '@/shared/analytics/charts'
import PwaPage from '../components/PwaPage.vue'

/**
 * Раскрытая диаграмма на телефоне: подробная диаграмма (широкая прокручивается вбок) и под ней сведения о диаграмме.
 */
const props = defineProps<{ id: string }>()

const router = useRouter()
const chart = computed(() => chartById(props.id))
// закономерность с цифрами — от диаграммы, после загрузки данных
const find = ref('')

// назад — в дашборд, с которого пришли; открыли напрямую — в дашборд
function goBack() {
  if (window.history.state?.back) router.back()
  else void router.replace({ name: 'pwa-analytics' })
}
</script>

<style scoped lang="scss">
.chart-scroll {
  overflow-x: auto;
}

.chart-scroll__body {
  display: flex;
  flex-direction: column;
}
</style>
