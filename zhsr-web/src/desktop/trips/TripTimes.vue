<template>
  <span>{{ departedDt ? t('Отправление') : t('Плановое отправление') }}</span>
  <span>
    {{ formatDateTime(departedDt ?? plannedDt) }}
    <span v-if="departedDt" class="zs-sub">• {{ delayText(plannedDt, departedDt) }}</span>
  </span>
  <span>{{ arrivedDt ? t('Прибытие') : t('Плановое прибытие') }}</span>
  <span>
    {{ formatDateTime(arrivedDt ?? arrival) }}
    <span v-if="arrivedDt" class="zs-sub">• {{ delayText(arrival, arrivedDt) }}</span>
  </span>
</template>

<script setup lang="ts">
import { computed } from 'vue'

import { t } from '@/boot/i18n'
import { formatDateTime } from '@/shared/format'
import { delayText, plannedArrival } from '@/shared/trips/tripTimes'

/**
 * Отправление и прибытие рейса строками полей (SidePanelFields): пока не было — плановое время с подписью «Плановое…»,
 * когда было — фактическое и насколько разошлось с планом. Плановое прибытие — плановое отправление плюс время в пути.
 */
const props = defineProps<{
  plannedDt: string
  departedDt: string | null
  arrivedDt: string | null
  /** время в пути, мин */
  duration: number
}>()

const arrival = computed(() => plannedArrival(props.plannedDt, props.duration))
</script>
