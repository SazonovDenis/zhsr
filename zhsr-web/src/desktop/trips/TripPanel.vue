<template>
  <SidePanel
    :empty="trip ? '' : 'Выберите рейс в списке'"
    :title="trip ? t('Поезд № {train}', { train: trip.train }) : ''"
    :subtitle="trip?.routeName ?? ''"
    @close="emit('close')"
  >
    <SidePanelFields v-if="trip">
      <span>{{ t('Вид маршрута') }}</span>
      <span>{{ t(trip.routeKind) }}</span>
      <span>{{ t('Время в пути') }}</span>
      <span>{{ t('{n} часов', trip.durationHours) }}</span>
      <span>{{ t('Отправление') }}</span>
      <span>{{ formatDateTime(trip.planned) }}</span>
      <span>{{ t('Состояние') }}</span>
      <span><DictBadge :id="trip.state" dict="TripState" /></span>
    </SidePanelFields>
  </SidePanel>
</template>

<script setup lang="ts">
import type { Trip } from '@/api/trips'
import { t } from '@/boot/i18n'
import DictBadge from '@/shared/components/DictBadge.vue'
import { formatDateTime } from '@/shared/format'
import SidePanel from '../frame/SidePanel.vue'
import SidePanelFields from '../frame/SidePanelFields.vue'

/** Правая панель списка рейсов: карточка выбранного рейса. */
defineProps<{ trip: Trip | null }>()

const emit = defineEmits<{ close: [] }>()
</script>
