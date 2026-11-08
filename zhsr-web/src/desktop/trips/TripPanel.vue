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
    <!-- действия: без прав — кнопки нет; не тот статус рейса — кнопка неактивна, причина рядом -->
    <div v-if="trip" class="trip-panel__actions">
      <template v-for="b in buttons" :key="b.label">
        <button type="button" class="trip-panel__btn q-py-none q-px-md" :disabled="!!b.reason" @click="b.run">
          {{ t(b.label) }}
        </button>
        <span class="trip-panel__reason">{{ b.reason }}</span>
      </template>
    </div>
  </SidePanel>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { Notify } from 'quasar'
import { useRouter } from 'vue-router'

import type { TripRow, TripCrew } from '@/api/trips'
import { t } from '@/boot/i18n'
import { formatHm } from '@/desktop/routes/duration'
import { openDialog } from '@/shared/components/dialog/openDialog'
import DictBadge from '@/shared/components/DictBadge.vue'
import { CrewDuty, RoleType, TripStatus } from '@/shared/constants/systemDicts'
import { dictManager } from '@/shared/dict/DictManager'
import { currentUser, hasRole, loadCurrentUser } from '@/shared/user'
import SidePanel from '../frame/SidePanel.vue'
import SidePanelFields from '../frame/SidePanelFields.vue'
import TripCancelDialog from './TripCancelDialog.vue'
import TripMarkDialog from './TripMarkDialog.vue'
import TripNewDialog from './TripNewDialog.vue'
import TripTimes from './TripTimes.vue'
import { markEditable, useNow } from '@/shared/trips/tripTimes'

/**
 * Правая панель списка рейсов: карточка выбранного рейса, переход к формированию рейса и отметки отправления и
 * прибытия у начальника поезда этого рейса и администратора. Событие changed — рейс изменился.
 */
const props = defineProps<{ trip: TripRow | null }>()

const emit = defineEmits<{ close: []; changed: [] }>()

const router = useRouter()

// отправление и прибытие отмечают начальник поезда этого рейса и администратор; проверки — на сервере
const isChief = computed(() => {
  const own = currentUser.value?.trip
  return !!props.trip && own?.id === props.trip.id && own.crewDutyType === CrewDuty.LNP
})
const isAdmin = computed(() => hasRole(currentUser.value, RoleType.ADMIN))
const canMark = computed(() => isChief.value || isAdmin.value)

// исправить или отменить отметку можно 10 минут после неё
const now = useNow()
const fixDeparture = computed(
  () =>
    canMark.value &&
    props.trip?.tripStatusType === TripStatus.IN_PROGRESS &&
    markEditable(props.trip.departureMarkedDt, now.value),
)
const fixArrival = computed(
  () =>
    canMark.value &&
    props.trip?.tripStatusType === TripStatus.DONE &&
    markEditable(props.trip.arrivalMarkedDt, now.value),
)

// редактирует и отменяет рейс планировщик вагонов
const isPlanner = computed(() => hasRole(currentUser.value, RoleType.WAGON_PLANNER) || isAdmin.value)
// акт составляют начальник поезда этого рейса и мастер депо станции маршрута; станцию проверяет сервер
const canCoupleRole = computed(() => canMark.value || hasRole(currentUser.value, RoleType.MASTER))

/** Почему действие недоступно в текущем статусе рейса; пусто — доступно. */
function statusReason(...allowed: number[]): string {
  const s = props.trip?.tripStatusType
  if (s !== undefined && allowed.includes(s)) return ''
  return t('Недоступно в статусе «{status}»', { status: dictManager.getValue('TripState', s) })
}
const FIX_REASON = 'Исправить можно в течение 10 минут после отметки'

/** Кнопки панели: только те, на которые есть права; reason — почему недоступно сейчас. */
const buttons = computed(() => {
  if (!props.trip) return []
  const all = [
    { label: 'Формирование', show: true, reason: '', run: openForm },
    { label: 'Редактировать', show: isPlanner.value, reason: statusReason(TripStatus.PLANNED), run: editTrip },
    {
      label: 'Отметить отправление',
      show: canMark.value,
      reason: statusReason(TripStatus.PLANNED),
      run: () => mark(true),
    },
    {
      label: 'Отметить прибытие',
      show: canMark.value,
      reason: statusReason(TripStatus.IN_PROGRESS),
      run: () => mark(false),
    },
    {
      label: 'Исправить отправление',
      show: canMark.value,
      reason: statusReason(TripStatus.IN_PROGRESS) || (fixDeparture.value ? '' : t(FIX_REASON)),
      run: () => mark(true, true),
    },
    {
      label: 'Исправить прибытие',
      show: canMark.value,
      reason: statusReason(TripStatus.DONE) || (fixArrival.value ? '' : t(FIX_REASON)),
      run: () => mark(false, true),
    },
    {
      label: 'Отцепка',
      show: canCoupleRole.value,
      reason: statusReason(TripStatus.IN_PROGRESS),
      run: () => couple('detach'),
    },
    {
      label: 'Прицепка',
      show: canCoupleRole.value,
      reason: statusReason(TripStatus.IN_PROGRESS),
      run: () => couple('attach'),
    },
    { label: 'Отменить рейс', show: isPlanner.value, reason: statusReason(TripStatus.PLANNED), run: cancel },
  ]
  return all.filter((b) => b.show)
})

onMounted(() => void loadCurrentUser())

function couple(kind: string) {
  void router.push({ name: 'trip-coupling', params: { id: props.trip!.id, kind } })
}

async function editTrip() {
  const trip = props.trip!
  const saved = await openDialog<TripRow>(TripNewDialog, {
    trip: { id: trip.id, route: trip.route, plannedDt: trip.plannedDt },
  })
  if (saved) emit('changed')
}

async function cancel() {
  const trip = props.trip!
  const done = await openDialog<boolean>(TripCancelDialog, {
    tripId: trip.id,
    tripName: `${trip.trainNumber} ${trip.name}`,
  })
  if (!done) return
  Notify.create({ message: t('Рейс отменён'), type: 'positive', position: 'bottom' })
  emit('changed')
}

function openForm() {
  void router.push({ name: 'trip-form', params: { id: props.trip!.id } })
}

// отметить; fix — исправить время уже сделанной отметки или отменить её
async function mark(depart: boolean, fix = false) {
  const trip = props.trip!
  const markedTime = depart ? trip.departedDt : trip.arrivedDt
  const result = await openDialog<{ crew: TripCrew; message: string }>(TripMarkDialog, {
    tripId: trip.id,
    depart,
    fix: fix ? markedTime : null,
  })
  if (!result) return
  Notify.create({ message: result.message, type: 'positive', position: 'bottom' })
  void loadCurrentUser(true)
  emit('changed')
}
</script>

<style scoped lang="scss">
.trip-panel__actions {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  align-items: center;
  gap: 0.5rem;
}

.trip-panel__reason {
  color: $zs-text-muted;
  font-size: 0.8rem;
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
