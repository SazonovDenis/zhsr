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
    <div v-if="trip" class="trip-panel__actions">
      <button type="button" class="trip-panel__btn q-py-none q-px-md" @click="openForm">{{ t('Формирование') }}</button>
      <button v-if="canEditTrip" type="button" class="trip-panel__btn q-py-none q-px-md" @click="editTrip">
        {{ t('Редактировать') }}
      </button>
      <button v-if="canDepart" type="button" class="trip-panel__btn q-py-none q-px-md" @click="mark(true)">
        {{ t('Отметить отправление') }}
      </button>
      <button v-if="canArrive" type="button" class="trip-panel__btn q-py-none q-px-md" @click="mark(false)">
        {{ t('Отметить прибытие') }}
      </button>
      <button v-if="fixDeparture" type="button" class="trip-panel__btn q-py-none q-px-md" @click="mark(true, true)">
        {{ t('Исправить отправление') }}
      </button>
      <button v-if="fixArrival" type="button" class="trip-panel__btn q-py-none q-px-md" @click="mark(false, true)">
        {{ t('Исправить прибытие') }}
      </button>
      <template v-if="canCouple">
        <button type="button" class="trip-panel__btn q-py-none q-px-md" @click="couple('detach')">
          {{ t('Отцепка') }}
        </button>
        <button type="button" class="trip-panel__btn q-py-none q-px-md" @click="couple('attach')">
          {{ t('Прицепка') }}
        </button>
      </template>
      <button v-if="canCancel" type="button" class="trip-panel__btn q-py-none q-px-md" @click="cancel">
        {{ t('Отменить рейс') }}
      </button>
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
const canDepart = computed(() => canMark.value && props.trip?.tripStatusType === TripStatus.PLANNED)
const canArrive = computed(() => canMark.value && props.trip?.tripStatusType === TripStatus.IN_PROGRESS)

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

const planned = computed(() => props.trip?.tripStatusType === TripStatus.PLANNED)
const canEditTrip = computed(() => planned.value && (hasRole(currentUser.value, RoleType.PLANNER) || isAdmin.value))
const canCancel = canEditTrip
// акт составляют начальник поезда этого рейса и мастер депо станции маршрута; станцию проверяет сервер
const canCouple = computed(
  () =>
    props.trip?.tripStatusType === TripStatus.IN_PROGRESS &&
    (canMark.value || hasRole(currentUser.value, RoleType.MASTER)),
)

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

  &:hover {
    background: $zs-bg-hover;
  }
}
</style>
