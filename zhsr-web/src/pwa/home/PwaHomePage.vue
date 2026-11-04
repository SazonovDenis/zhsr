<template>
  <PwaPage>
    <!-- текущий рейс пользователя -->
    <div v-if="trip" class="today__trip">
      <div class="today__trip-head">
        <span class="today__trip-train">{{ t('Рейс {train}', { train: trip.trainNumber }) }}</span>
        <DictBadge :id="trip.tripStatusType" dict="TripState" />
      </div>
      <div class="today__trip-name">{{ trip.name }}</div>
      <div class="today__tiles">
        <div v-if="trip.crewDutyType === CrewDuty.CONDUCTOR && trip.wagons.length > 0" class="today__tile">
          <span class="today__tile-key">{{ t('Мой вагон') }}</span>
          <span class="today__tile-value zs-mono">{{ trip.wagons.join(', ') }}</span>
        </div>
        <button v-else type="button" class="today__tile" :disabled="!canDepart" @click="openMark(true)">
          <span class="today__tile-key">{{ t('Отправление') }}</span>
          <span class="today__tile-value">{{ formatDateTime(trip.departure) }}</span>
        </button>
        <button type="button" class="today__tile" :disabled="!canArrive" @click="openMark(false)">
          <span class="today__tile-key">{{ t('Прибытие') }}</span>
          <span class="today__tile-value">{{ formatDateTime(trip.arrival) }}</span>
        </button>
      </div>
      <!-- начальник поезда распределяет проводников по вагонам -->
      <router-link v-if="trip.crewDutyType === CrewDuty.LNP" :to="{ name: 'pwa-trip' }" class="today__crew">
        <span>{{ t('Формирование • проводники') }}</span>
        <span v-if="norm" class="today__norm" :class="{ 'today__norm--ok': normDone(norm) }">
          {{ t('{have} из {need}', { have: norm.have, need: norm.need }) }}
        </span>
      </router-link>
      <div v-if="canArrive" class="today__coupling">
        <router-link :to="couplingLink(trip.id, 'detach')" class="today__coupling-link">{{ t('Отцепка') }}</router-link>
        <router-link :to="couplingLink(trip.id, 'attach')" class="today__coupling-link">{{
          t('Прицепка')
        }}</router-link>
      </div>
    </div>

    <!-- мастер депо: рейсы в пути через станцию депо -->
    <div v-for="tr in depotTrips" :key="tr.id" class="today__trip">
      <div class="today__trip-name">{{ tr.trainNumber }} {{ tr.name }}</div>
      <div class="today__coupling">
        <router-link :to="couplingLink(tr.id, 'detach')" class="today__coupling-link">{{ t('Отцепка') }}</router-link>
        <router-link :to="couplingLink(tr.id, 'attach')" class="today__coupling-link">{{ t('Прицепка') }}</router-link>
      </div>
    </div>

    <div v-if="stale" class="today__stale">{{ t('Нет связи — показаны сохранённые данные') }}</div>
    <ErrorBox v-if="error" :error="error" :retry="load" class="today__error" />
    <LoadSpinner v-if="busy.active.value" />

    <RequestSection
      v-for="s in sections"
      :key="s.key"
      :title="t(s.title)"
      :requests="s.requests"
      :queued="s.queued"
      :empty="t(s.empty)"
    />

    <!-- отметка отправления или прибытия -->
    <BottomSheet
      :show="marking !== null"
      :title="marking ? t('Отметить отправление') : t('Отметить прибытие')"
      @update:show="(v) => !v && (marking = null)"
    >
      <input v-model="markTime" type="datetime-local" class="today__time" />
      <ErrorBox v-if="markError" :error="markError" />
      <template #buttons>
        <button type="button" class="today__sheet-btn" @click="marking = null">{{ t('Отмена') }}</button>
        <button
          type="button"
          class="today__sheet-btn today__sheet-btn--primary"
          :disabled="!markTime || markRunning"
          @click="confirmMark"
        >
          {{ t('Подтвердить') }}
        </button>
      </template>
    </BottomSheet>
  </PwaPage>
</template>

<script setup lang="ts">
import { date } from 'quasar'
import { computed, onBeforeUnmount, ref, shallowRef } from 'vue'

import type { RepairRequest } from '@/api/requests'
import { fetchRequests, requestFilter } from '@/api/requests'
import type { TripRow, TripCrew } from '@/api/trips'
import { fetchTripCrew, fetchTrips, markArrival, markDeparture } from '@/api/trips'
import { t } from '@/boot/i18n'
import { bus } from '@/shared/bus'
import { dictManager } from '@/shared/dict/DictManager'
import { provideBusy } from '@/shared/busy'
import DictBadge from '@/shared/components/DictBadge.vue'
import ErrorBox from '@/shared/components/ErrorBox.vue'
import type { ShownError } from '@/shared/errors'
import { shownError } from '@/shared/errors'
import LoadSpinner from '@/shared/components/LoadSpinner.vue'
import { formatDateTime, isoDateTime } from '@/shared/format'
import { isOnline } from '@/shared/network'
import { loadCached } from '@/shared/offline/cache'
import type { QueuedRequest } from '@/shared/offline/outbox'
import { outbox, outboxAsRequest } from '@/shared/offline/outbox'
import { CrewDuty, RequestStatus, RoleType, ServiceType, TripStatus } from '@/shared/constants/systemDicts'
import { canAccept, canWork } from '@/shared/requests/rights'
import { currentUser, hasRole, loadCurrentUser } from '@/shared/user'
import { crewNorm, normDone } from '@/shared/trips/crewNorm'
import BottomSheet from '../components/BottomSheet.vue'
import PwaPage from '../components/PwaPage.vue'
import RequestSection from '../components/RequestSection.vue'

/**
 * Главный экран мобильной части «Сегодня»: текущий рейс пользователя и разделы заявок по его ролям. Роли совмещаются,
 * поэтому разделы набираются по всем ролям пользователя:
 * - проводник и начальник поезда — «Ждут вашей приёмки» и «Ваши открытые заявки»; у начальника поезда в карточке
 *   рейса — переход к формированию проводников, клики по отправлению и прибытию отмечают их;
 * - мастер депо и администратор — рейсы в пути с переходом к отцепке и прицепке;
 * - сотрудник службы (ПЭМ, мастер депо, клининг, экипировщик) — «На исполнение» и «Ждут приёмки автора»;
 * - инструктор поездных бригад и эксплуатационщик — «Ждут вашей приёмки».
 * Без связи показываются сохранённые на телефоне данные.
 */

const user = computed(() => currentUser.value)
const trip = computed(() => user.value?.trip ?? null)

const requests = shallowRef<RepairRequest[]>([])
const stale = ref(false)
const error = ref<ShownError | null>(null)
const crew = shallowRef<TripCrew | null>(null)
const norm = computed(() => (crew.value ? crewNorm(crew.value) : null))

// отправление и прибытие отмечают начальник поезда и администратор; нужна связь
const isChief = computed(
  () => (trip.value?.crewDutyType === CrewDuty.LNP || hasRole(user.value, RoleType.ADMIN)) && isOnline.value,
)
const canDepart = computed(() => isChief.value && trip.value?.tripStatusType === TripStatus.PLANNED)
const canArrive = computed(() => isChief.value && trip.value?.tripStatusType === TripStatus.IN_PROGRESS)

const couplingLink = (id: number, kind: string) => ({ name: 'pwa-trip-coupling', params: { id, kind } })

// рейсы в пути для отцепки и прицепки: у мастера сервер ограничивает их станцией депо, администратору видны все
const depotTrips = shallowRef<TripRow[]>([])

// отметка: true — отправление, false — прибытие, null — лист закрыт
const marking = ref<boolean | null>(null)
const markTime = ref('')
const markError = ref<ShownError | null>(null)
const markRunning = ref(false)

function openMark(depart: boolean) {
  markTime.value = date.formatDate(new Date(), 'YYYY-MM-DDTHH:mm')
  markError.value = null
  marking.value = depart
}

async function confirmMark() {
  const current = trip.value
  if (!current) return
  markRunning.value = true
  markError.value = null
  try {
    const mark = marking.value ? markDeparture : markArrival
    await mark(current.id, isoDateTime(new Date(markTime.value)))
    marking.value = null
    await loadCurrentUser(true)
    await load()
  } catch (e) {
    markError.value = shownError(e)
  } finally {
    markRunning.value = false
  }
}

const OPEN_WORK: number[] = [
  RequestStatus.NEW,
  RequestStatus.ASSIGNED,
  RequestStatus.IN_PROGRESS,
  RequestStatus.DEFERRED,
]

interface Section {
  key: string
  title: string
  empty: string
  requests: RepairRequest[]
  queued: QueuedRequest[]
}

const sections = computed<Section[]>(() => {
  const u = user.value
  if (!u) return []
  const list = requests.value
  const result: Section[] = []
  const add = (s: Section) => {
    if (!result.some((x) => x.key === s.key)) result.push(s)
  }
  const accept = () =>
    add({
      key: 'accept',
      title: 'Ждут вашей приёмки',
      empty: 'Нет работ на приёмку',
      requests: list.filter((r) => r.requestStatusType === RequestStatus.DONE && canAccept(u, r)),
      queued: [],
    })

  if (hasRole(u, RoleType.CONDUCTOR) || hasRole(u, RoleType.LNP)) {
    accept()
    add({
      key: 'mine',
      title: 'Ваши открытые заявки',
      empty: 'Открытых заявок нет',
      requests: list.filter((r) => r.createdByUsr.id === u.id && r.requestStatusType !== RequestStatus.DONE),
      queued: outbox.value.filter((x) => x.author === u.id).map((x) => outboxAsRequest(x, u.name)),
    })
  }
  const workServices: number[] = [ServiceType.TECHNICAL, ServiceType.CLEANING, ServiceType.EQUIPMENT]
  if (u.services.some((s) => workServices.includes(s))) {
    add({
      key: 'todo',
      title: 'На исполнение',
      empty: 'Всё сделано',
      requests: list.filter(
        (r) => OPEN_WORK.includes(r.requestStatusType) && (r.executorUsr?.id === u.id || canWork(u, r)),
      ),
      queued: [],
    })
    add({
      key: 'waiting',
      title: 'Ждут приёмки автора',
      empty: 'Нет работ на приёмку',
      requests: list.filter((r) => r.requestStatusType === RequestStatus.DONE && r.executorUsr?.id === u.id),
      queued: [],
    })
  }
  if (hasRole(u, RoleType.INSTRUCTOR) || hasRole(u, RoleType.OPERATOR) || hasRole(u, RoleType.ADMIN)) accept()
  return result
})

// общее ожидание страницы: спиннер на месте содержимого — на всю цепочку загрузок
const busy = provideBusy()

async function load() {
  error.value = null
  try {
    await busy.run(async () => {
      // справочник неисправностей для строк заявок — параллельно с пользователем
      const [u] = await Promise.all([loadCurrentUser(), dictManager.prepareDict('FaultType')])
      const result = await loadCached('today', () => fetchRequests(requestFilter('all', { closed: false })))
      requests.value = result.data
      stale.value = result.stale
      if (u.trip?.crewDutyType === CrewDuty.LNP) {
        const id = u.trip.id
        crew.value = (await loadCached(`crew:${id}`, () => fetchTripCrew(id))).data
      }
      if (hasRole(u, RoleType.MASTER) || hasRole(u, RoleType.ADMIN)) {
        const filter = { route: null, station: null, from: null, to: null, states: [TripStatus.IN_PROGRESS] }
        depotTrips.value = (await loadCached('depot-trips', () => fetchTrips(filter, null))).data
      }
    })
  } catch (e) {
    error.value = shownError(e)
  }
}

const onRefresh = () => void load()
bus.on('requests-refresh', onRefresh)
bus.on('connection-restored', onRefresh)
onBeforeUnmount(() => {
  bus.off('requests-refresh', onRefresh)
  bus.off('connection-restored', onRefresh)
})

// загрузка — сразу в setup: спиннер виден с первой отрисовки
void load()
</script>

<style scoped lang="scss">
.today__trip {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  padding: 1rem;
  border: 0.0625rem solid $zs-border;
  border-radius: 0.375rem;
  background: #fff;
}

.today__trip-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
}

.today__trip-train {
  color: $zs-text-muted;
  font-size: 0.75rem;
  font-weight: 700;
  letter-spacing: 0.06em;
  text-transform: uppercase;
}

.today__trip-name {
  font-size: 1.0625rem;
  font-weight: 600;
  line-height: 1.3;
}

.today__tiles {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0.625rem;
}

.today__tile {
  display: flex;
  flex-direction: column;
  gap: 0.125rem;
  padding: 0.625rem 0.75rem;
  border: 0.0625rem solid transparent;
  border-radius: 0.25rem;
  background: $zs-row-selected-bg;
  color: inherit;
  font: inherit;
  text-align: left;

  // плитка, по которой отмечается отправление или прибытие
  &:enabled:is(button) {
    border-color: $primary;
    cursor: pointer;
  }
}

.today__coupling {
  display: flex;
  gap: 0.5rem;
}

.today__coupling-link {
  flex: 1;
  padding: 0.625rem 0.75rem;
  border: 0.0625rem solid $primary;
  border-radius: 0.25rem;
  color: $primary;
  font-size: 0.9375rem;
  font-weight: 600;
  text-align: center;
  text-decoration: none;
}

.today__time {
  width: 100%;
  height: 3.3rem;
  padding: 0 0.875rem;
  border: 0.0625rem solid $zs-border-input;
  border-radius: 0.25rem;
  background: #fff;
  color: $zs-text;
  font: inherit;
  font-size: 1rem;
}

.today__sheet-btn {
  flex: 1;
  min-height: 3.3rem;
  padding: 0 0.875rem;
  border: 0.0625rem solid $zs-border;
  border-radius: 0.375rem;
  background: #fff;
  color: $zs-text;
  font: inherit;
  font-size: 0.9375rem;
  font-weight: 700;
  cursor: pointer;

  &--primary {
    border: 0;
    background: $primary;
    color: #fff;
  }

  &:disabled {
    opacity: 0.4;
  }
}

.today__tile-key {
  color: $zs-text-muted;
  font-size: 0.75rem;
}

.today__tile-value {
  font-size: 1.0625rem;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

.today__crew {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
  padding-top: 0.625rem;
  border-top: 0.0625rem solid $zs-border;
  color: $primary;
  font-size: 0.9375rem;
  font-weight: 600;
  text-decoration: none;
}

.today__norm {
  flex: none;
  padding: 0.125rem 0.5rem;
  border-radius: 62.5rem;
  background: #fbe9cf;
  color: #8a4b05;
  font-size: 0.8125rem;

  &--ok {
    background: #dff0e2;
    color: #1d5e2c;
  }
}

.today__stale {
  color: #7a5200;
  font-size: 0.8125rem;
}

.today__error {
  padding: 0.625rem 0.875rem;
  border-radius: 0.25rem;
  background: $zs-error-bg;
  color: $zs-error-fg;
  font-size: 0.8125rem;
}
</style>
