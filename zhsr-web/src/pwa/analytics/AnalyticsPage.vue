<template>
  <PwaPage>
    <AnalyticsGrid @open="open" />
  </PwaPage>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'

import AnalyticsGrid from '@/shared/analytics/AnalyticsGrid.vue'
import PwaPage from '../components/PwaPage.vue'

/** Дашборд «Аналитика» на телефоне: диаграммы друг под другом; нажатие открывает диаграмму (AnalyticsChartPage). */
const router = useRouter()

function open(id: string) {
  void router.push({ name: 'pwa-analytics-chart', params: { id } })
}
</script>
