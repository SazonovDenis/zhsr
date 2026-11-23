<template>
  <DesktopFrame>
    <template #header />
    <AnalyticsGrid class="q-pb-md" @open="open" />
  </DesktopFrame>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'

import AnalyticsGrid from '@/shared/analytics/AnalyticsGrid.vue'
import DesktopFrame from '../frame/DesktopFrame.vue'

/** Дашборд «Аналитика»: сетка диаграмм; клик открывает диаграмму в окне раскрытой диаграммы (AnalyticsChartPage). */
const router = useRouter()

function open(id: string) {
  void router.push({ name: 'analytics-chart', params: { id } })
}
</script>
