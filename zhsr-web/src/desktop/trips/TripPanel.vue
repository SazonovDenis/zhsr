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
    <div v-if="trip && canPickCrew" class="trip-panel__actions">
      <button type="button" class="trip-panel__btn" @click="pickCrew">{{ t('Проводники…') }}</button>
    </div>
  </SidePanel>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { Notify } from 'quasar'

import type { Trip } from '@/api/trips'
import { fetchTripCrew } from '@/api/trips'
import { t } from '@/boot/i18n'
import { openDialog } from '@/shared/components/dialog/openDialog'
import DictBadge from '@/shared/components/DictBadge.vue'
import { dictManager } from '@/shared/dict/DictManager'
import { formatDateTime } from '@/shared/format'
import { currentUser, hasRole, loadCurrentUser } from '@/shared/user'
import SidePanel from '../frame/SidePanel.vue'
import SidePanelFields from '../frame/SidePanelFields.vue'
import CrewPickDialog from './CrewPickDialog.vue'

/**
 * Правая панель списка рейсов: карточка выбранного рейса. Планировщик набирает проводников рейса в статусе «План»
 * кнопкой «Проводники…» (Ф-РЕЙ-06).
 */
const props = defineProps<{ trip: Trip | null }>()

const emit = defineEmits<{ close: [] }>()

const canPickCrew = computed(
  () =>
    props.trip?.state === 'PLANNED' && (hasRole(currentUser.value, 'PLANNER') || hasRole(currentUser.value, 'ADMIN')),
)

onMounted(() => void loadCurrentUser())

async function pickCrew() {
  const trip = props.trip
  if (!trip) return
  const crew = await fetchTripCrew(trip.id)
  const saved = await openDialog<boolean>(CrewPickDialog, {
    tripId: trip.id,
    tripName: `${trip.train} ${trip.routeName}`,
    station: dictManager.getValue('Station', crew.trip.departureStation),
  })
  if (saved) Notify.create({ message: t('Проводники рейса сохранены'), type: 'positive', position: 'bottom' })
}
</script>

<style scoped lang="scss">
.trip-panel__actions {
  display: flex;
  gap: 0.5rem;
}

.trip-panel__btn {
  height: var(--zs-control-height);
  padding: 0 0.875rem;
  border: 0.0625rem solid $zs-border-input;
  border-radius: 0.25rem;
  background: #fff;
  color: $primary;
  font: inherit;
  font-size: 0.8125rem;
  font-weight: 600;
  cursor: pointer;

  &:hover {
    background: $zs-bg-hover;
  }
}
</style>
