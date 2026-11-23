<template>
  <DesktopFrame v-model:floating="floating" :back="back" :right-open="!!chart && aboutOpen">
    <template #header>
      <h1 class="desktop-frame__title">{{ chart ? t(chart.title) : t('Диаграмма не найдена') }}</h1>
      <div v-if="chart && !aboutOpen" class="desktop-frame__actions">
        <SoftLink text="О диаграмме" @click="aboutOpen = true" />
      </div>
    </template>

    <component :is="chart.component" v-if="chart" class="q-pb-md" detailed @find="find = $event" />

    <template v-if="chart" #right>
      <SidePanel :title="t('О диаграмме')" @close="aboutOpen = false">
        <ChartAbout :chart="chart" :find="find" />
      </SidePanel>
    </template>
  </DesktopFrame>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'

import { t } from '@/boot/i18n'
import ChartAbout from '@/shared/analytics/ChartAbout.vue'
import { chartById } from '@/shared/analytics/charts'
import SoftLink from '@/shared/components/form/SoftLink.vue'
import DesktopFrame from '../frame/DesktopFrame.vue'
import SidePanel from '../frame/SidePanel.vue'
import { aboutOpen } from './aboutPanel'

/**
 * Окно раскрытой диаграммы: подробная диаграмма (с ФИО и переключателями) и правая панель — закономерность, цель,
 * решаемая проблема, как читать. Панель закрывается крестиком; пока она скрыта, в заголовке ссылка «О диаграмме» — открыть
 * снова. Скрыта ли панель — общее для всех диаграмм. «Назад» — на дашборд «Аналитика».
 */
const props = defineProps<{ id: string }>()

const router = useRouter()
const back = () => void router.push({ name: 'analytics' })

const chart = computed(() => chartById(props.id))
const floating = ref(false)
// закономерность с цифрами — от диаграммы, после загрузки данных
const find = ref('')
</script>
