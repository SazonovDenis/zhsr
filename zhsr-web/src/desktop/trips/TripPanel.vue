<template>
  <SidePanel
    :empty="trip ? '' : 'Выберите рейс в списке'"
    :title="trip ? t('Поезд № {train}', { train: trip.trainNumber }) : ''"
    :subtitle="trip?.name ?? ''"
    @close="emit('close')"
  >
    <SidePanelFields v-if="trip">
      <span>{{ t('Вид маршрута') }}</span>
      <span>{{ dictManager.getValue('TransportType', trip.transportType) }}</span>
      <span>{{ t('Время в пути') }}</span>
      <span>{{ formatHm(trip.duration) }}</span>
      <TripTimes
        :planned-dt="trip.plannedDt"
        :departed-dt="trip.departedDt"
        :arrived-dt="trip.arrivedDt"
        :duration="trip.duration"
      />
      <span>{{ t('Состояние') }}</span>
      <span><DictBadge :id="trip.tripStatusType" dict="TripState" /></span>
    </SidePanelFields>
    <!-- действия: видны все; нет прав или не тот статус рейса — кнопка неактивна, причина во всплывающей подсказке -->
    <div v-if="trip" class="trip-panel__actions">
      <button
        v-for="b in buttons"
        :key="b.label"
        type="button"
        class="trip-panel__btn q-py-none q-px-md"
        :disabled="!!b.reason"
        :title="b.reason || undefined"
        @click="b.run()"
      >
        {{ t(b.label) }}
      </button>
    </div>
  </SidePanel>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'

import type { TripRow } from '@/api/trips'
import { t } from '@/boot/i18n'
import { formatHm } from '@/desktop/routes/duration'
import DictBadge from '@/shared/components/DictBadge.vue'
import { dictManager } from '@/shared/dict/DictManager'
import { loadCurrentUser } from '@/shared/user'
import SidePanel from '../frame/SidePanel.vue'
import SidePanelFields from '../frame/SidePanelFields.vue'
import { useTripActions } from './tripActions'
import TripTimes from './TripTimes.vue'

/**
 * Правая панель списка рейсов: карточка выбранного рейса и кнопки действий с рейсом (useTripActions) — те же, что в
 * меню строки списка. Событие changed — рейс изменился.
 */
const props = defineProps<{ trip: TripRow | null }>()

const emit = defineEmits<{ close: []; changed: [] }>()

const { actionsOf } = useTripActions(() => emit('changed'))

/** Кнопки панели: видны все; reason — почему недоступно сейчас. */
const buttons = computed(() => (props.trip ? actionsOf(props.trip) : []))

onMounted(() => void loadCurrentUser())
</script>

<style scoped lang="scss">
.trip-panel__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
}

.trip-panel__btn {
  height: var(--zs-control-height);
  border: 0.0625rem solid $zs-border-input;
  border-radius: 0.3rem;
  background: #fff;
  color: $primary;
  font: inherit;
  font-size: 0.8rem;
  font-weight: 600;
  cursor: pointer;

  &:hover:not(:disabled) {
    background: $zs-bg-hover;
  }

  &:disabled {
    color: $zs-text-muted;
    cursor: default;
  }
}
</style>
