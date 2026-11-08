<template>
  <PwaPage :stale="stale">
    <!-- текущий рейс пользователя или соседний (по ссылке) -->
    <div v-if="trip" class="today__trip q-pa-md">
      <!-- как строка заявки: заголовок, статус -->
      <div class="today__trip-head">
        <div class="today__trip-name">{{ trip.trainNumber }} • {{ trip.name }}</div>
        <div class="today__trip-badges"><DictBadge :id="trip.tripStatusType" dict="TripState" /></div>
      </div>
      <div class="today__tiles">
        <div v-if="trip.crewDutyType === CrewDuty.CONDUCTOR && trip.wagons.length > 0" class="today__tile">
          <span class="today__tile-key">{{ t('Мой вагон') }}</span>
          <span class="today__tile-value zs-mono">{{ trip.wagons.join(', ') }}</span>
        </div>
        <div
          v-else
          role="button"
          tabindex="0"
          class="today__tile q-py-sm q-px-md"
          :class="{ 'today__tile--action': canDepart || fixDeparture }"
          @click="canDepart ? openMark(true) : fixDeparture && openFix(true)"
        >
          <span class="today__tile-key">{{ trip.departedDt ? t('Отправление') : t('Плановое отправление') }}</span>
          <span class="today__tile-value">{{ formatDateTime(trip.departedDt ?? trip.plannedDt) }}</span>
          <span v-if="trip.departedDt" class="today__tile-delay">{{ delayText(trip.plannedDt, trip.departedDt) }}</span>
        </div>
        <div
          role="button"
          tabindex="0"
          class="today__tile q-py-sm q-px-md"
          :class="{ 'today__tile--action': canArrive || fixArrival }"
          @click="canArrive ? openMark(false) : fixArrival && openFix(false)"
        >
          <span class="today__tile-key">{{ trip.arrivedDt ? t('Прибытие') : t('Плановое прибытие') }}</span>
          <span class="today__tile-value">{{ formatDateTime(trip.arrivedDt ?? trip.arrival) }}</span>
          <span v-if="trip.arrivedDt" class="today__tile-delay">{{ delayText(trip.arrival, trip.arrivedDt) }}</span>
        </div>
      </div>
      <div v-if="canArrive" class="today__coupling q-pt-md">
        <router-link :to="couplingLink(trip.id, 'detach')" class="today__coupling-link q-py-sm q-px-md">{{
          t('Отцепка')
        }}</router-link>
        <router-link :to="couplingLink(trip.id, 'attach')" class="today__coupling-link q-py-sm q-px-md">{{
          t('Прицепка')
        }}</router-link>
      </div>
      <!-- отметку можно отменить, пока её можно исправить: 10 минут после неё -->
      <button
        v-if="fixDeparture || fixArrival"
        type="button"
        class="today__cancel-mark q-py-sm q-px-md"
        :disabled="markRunning"
        @click="cancelMark(fixDeparture)"
      >
        {{ fixDeparture ? t('Отменить отметку об отправлении') : t('Отменить отметку о прибытии') }}
      </button>
      <SoftLink v-if="switchTo" :text="switchText" class="today__switch" @click="showOtherTrip = !showOtherTrip" />
    </div>

    <!-- начальник поезда распределяет проводников по вагонам; оформление — как у разделов заявок -->
    <section v-if="trip?.crewDutyType === CrewDuty.LNP" class="today__crew">
      <div class="today__crew-card q-pa-md">
        <div v-if="norm" class="today__crew-attrs">
          <span>{{ t('Проводники') }}</span>
          <span>
            <span class="today__norm q-py-none q-px-sm" :class="{ 'today__norm--ok': normDone(norm) }">
              {{ t('{have} из {need}', { have: norm.have, need: norm.need }) }}
            </span>
          </span>
          <span>{{ t('Вагоны') }}</span>
          <span>{{ norm.wagons.length }}</span>
          <span>{{ t('ЛНП') }}</span>
          <span>{{ memberName(CrewDuty.LNP) }}</span>
          <span>{{ t('ПЭМ') }}</span>
          <span>{{ memberName(CrewDuty.PEM) }}</span>
        </div>
        <SoftLink text="Формирование" class="today__crew-link" @click="router.push({ name: 'pwa-trip' })" />
      </div>
    </section>

    <!-- мастер депо: рейсы в пути через станцию депо -->
    <div v-for="tr in depotTrips" :key="tr.id" class="today__trip q-pa-md">
      <div class="today__trip-name">{{ tr.trainNumber }} • {{ tr.name }}</div>
      <div class="today__coupling q-pt-md">
        <router-link :to="couplingLink(tr.id, 'detach')" class="today__coupling-link q-py-sm q-px-md">{{
          t('Отцепка')
        }}</router-link>
        <router-link :to="couplingLink(tr.id, 'attach')" class="today__coupling-link q-py-sm q-px-md">{{
          t('Прицепка')
        }}</router-link>
      </div>
    </div>

    <ErrorBox v-if="error" :error="error" :retry="load" class="today__error q-py-sm q-px-md" />
    <LoadSpinner v-if="busy.active.value" />

    <RequestSection
      v-for="s in sections"
      :key="s.key"
      :title="t(s.title)"
      :requests="s.requests"
      :queued="s.queued"
      :empty="t(s.empty)"
    />

    <!-- отметка отправления или прибытия: календарь во весь экран, без заголовка -->
    <CalendarDialog
      v-model="markTime"
      :show="marking !== null"
      with-time
      :label="marking ? 'Дата отправления' : 'Дата прибытия'"
      :running="markRunning"
      :ok-text="fixing ? 'Сохранить время' : 'Подтвердить'"
      @update:show="(v) => !v && (marking = null)"
      @confirm="confirmMark"
    >
      <ErrorBox v-if="markError" :error="markError" />
    </CalendarDialog>
  </PwaPage>
</template>

<script setup lang="ts">
import { date, Notify } from 'quasar'
import { computed, onBeforeUnmount, ref, shallowRef, watch } from 'vue'
import { useRouter } from 'vue-router'

import type { RepairRequest } from '@/api/requests'
import { fetchRequests, requestFilter } from '@/api/requests'
import type { TripRow, TripCrew } from '@/api/trips'
import {
  cancelArrival,
  cancelDeparture,
  fetchTripCrew,
  fetchTrips,
  markArrival,
  markDeparture,
  retimeArrival,
  retimeDeparture,
} from '@/api/trips'
import { t } from '@/boot/i18n'
import { bus } from '@/shared/bus'
import { dictManager } from '@/shared/dict/DictManager'
import { provideBusy } from '@/shared/busy'
import DictBadge from '@/shared/components/DictBadge.vue'
import ErrorBox from '@/shared/components/ErrorBox.vue'
import type { ShownError } from '@/shared/errors'
import { notifyError, shownError } from '@/shared/errors'
import LoadSpinner from '@/shared/components/LoadSpinner.vue'
import { formatDateTime, isoDateTime } from '@/shared/format'
import { isOnline } from '@/shared/network'
import { loadCached } from '@/shared/offline/cache'
import type { QueuedRequest } from '@/shared/offline/outbox'
import { outboxAsRequest, queuedRequests, withQueuedEdit } from '@/shared/offline/outbox'
import { CrewDuty, RequestStatus, RoleType, ServiceType, TripStatus } from '@/shared/constants/systemDicts'
import { canAccept, canWork } from '@/shared/requests/rights'
import { currentUser, hasRole, loadCurrentUser, userName } from '@/shared/user'
import { crewNorm, normDone } from '@/shared/trips/crewNorm'
import { delayText, markEditable, useNow } from '@/shared/trips/tripTimes'
import CalendarDialog from '../components/CalendarDialog.vue'
import SoftLink from '@/shared/components/form/SoftLink.vue'
import PwaPage from '../components/PwaPage.vue'
import { showOtherTrip } from './homeState'
import RequestSection from '../components/RequestSection.vue'

/**
 * Главный экран мобильной части «Рейс»: текущий рейс пользователя и разделы заявок по его ролям. Роли совмещаются,
 * поэтому разделы набираются по всем ролям пользователя:
 * - проводник и начальник поезда — «Ждут вашей приёмки» и «Ваши открытые заявки»; у начальника поезда в карточке
 *   рейса — переход к формированию проводников, клики по отправлению и прибытию отмечают их;
 * - мастер депо и администратор — рейсы в пути с переходом к отцепке и прицепке;
 * - сотрудник службы (ПЭМ, мастер депо, клининг, экипировщик) — «На исполнение» и «Ждут приёмки автора»;
 * - инструктор поездных бригад и эксплуатационщик — «Ждут вашей приёмки».
 * Без связи показываются сохранённые на телефоне данные.
 */

const user = computed(() => currentUser.value)
// показан текущий рейс или, по ссылке, соседний — предыдущий или следующий
const trip = computed(() => (showOtherTrip.value && user.value?.otherTrip) || user.value?.trip || null)
// рейс, на который переключает ссылка
const switchTo = computed(() => (showOtherTrip.value ? user.value?.trip : user.value?.otherTrip) ?? null)
const switchText = computed(() =>
  switchTo.value?.tripStatusType === TripStatus.DONE ? 'Показать предыдущий рейс' : 'Показать следующий рейс',
)

const requests = shallowRef<RepairRequest[]>([])
const router = useRouter()

const stale = ref(false)
const error = ref<ShownError | null>(null)
const crew = shallowRef<TripCrew | null>(null)
const norm = computed(() => (crew.value ? crewNorm(crew.value) : null))

// сотрудник бригады на должности; нет — «не назначен»
const memberName = (duty: number) => {
  const m = crew.value?.crew.find((x) => x.crewDutyType === duty)
  return m ? userName(m.usr) : t('не назначен')
}

// отправление и прибытие отмечают начальник поезда и администратор; нужна связь
const isChief = computed(
  () => (trip.value?.crewDutyType === CrewDuty.LNP || hasRole(user.value, RoleType.ADMIN)) && isOnline.value,
)
const canDepart = computed(() => isChief.value && trip.value?.tripStatusType === TripStatus.PLANNED)
const canArrive = computed(() => isChief.value && trip.value?.tripStatusType === TripStatus.IN_PROGRESS)

// отметку можно исправить или отменить 10 минут после неё; выполненный рейс остаётся текущим, пока нет нового
const now = useNow()
const fixDeparture = computed(() => canArrive.value && markEditable(trip.value?.departureMarkedDt, now.value))
const fixArrival = computed(
  () =>
    isChief.value &&
    trip.value?.tripStatusType === TripStatus.DONE &&
    markEditable(trip.value.arrivalMarkedDt, now.value),
)

const couplingLink = (id: number, kind: string) => ({ name: 'pwa-trip-coupling', params: { id, kind } })

// рейсы в пути для отцепки и прицепки: у мастера сервер ограничивает их станцией депо, администратору видны все
const depotTrips = shallowRef<TripRow[]>([])

// отметка: true — отправление, false — прибытие, null — лист закрыт
const marking = ref<boolean | null>(null)
const markTime = ref<string | null>(null)
const markError = ref<ShownError | null>(null)
const markRunning = ref(false)

// исправляется время уже сделанной отметки
const fixing = ref(false)

function openMark(depart: boolean) {
  markTime.value = date.formatDate(new Date(), 'YYYY-MM-DDTHH:mm')
  markError.value = null
  fixing.value = false
  marking.value = depart
}

async function confirmMark() {
  const current = trip.value
  if (!current) return
  markRunning.value = true
  markError.value = null
  try {
    const value = isoDateTime(new Date(markTime.value!))
    if (fixing.value) await (marking.value ? retimeDeparture : retimeArrival)(current.id, value)
    else await (marking.value ? markDeparture : markArrival)(current.id, value)
    marking.value = null
    await loadCurrentUser(true)
    await load()
  } catch (e) {
    markError.value = shownError(e)
  } finally {
    markRunning.value = false
  }
}

function openFix(depart: boolean) {
  const time = depart ? trip.value?.departedDt : trip.value?.arrivedDt
  markTime.value = date.formatDate(time ? new Date(time) : new Date(), 'YYYY-MM-DDTHH:mm')
  markError.value = null
  fixing.value = true
  marking.value = depart
}

// отменить отметку — кнопкой под панелью рейса, без окна; ошибка — всплывашкой
async function cancelMark(depart: boolean) {
  const current = trip.value
  if (!current) return
  markRunning.value = true
  try {
    await (depart ? cancelDeparture : cancelArrival)(current.id)
    Notify.create({ message: depart ? t('Отправление отменено') : t('Прибытие отменено'), position: 'bottom' })
    await loadCurrentUser(true)
    await load()
  } catch (e) {
    notifyError(e)
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
  // исправления, которые ждут отправки, видны сразу
  const list = requests.value.map(withQueuedEdit)
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

  // «Ваши открытые заявки» — тем, кто подаёт заявки сам: проводник, ЛНП, инструктор поездных бригад
  if (hasRole(u, RoleType.CONDUCTOR) || hasRole(u, RoleType.LNP) || hasRole(u, RoleType.INSTRUCTOR)) {
    accept()
    add({
      key: 'mine',
      title: 'Ваши открытые заявки',
      empty: 'Открытых заявок нет',
      requests: list.filter((r) => r.createdByUsr.id === u.id && r.requestStatusType !== RequestStatus.DONE),
      queued: queuedRequests.value.filter((x) => x.author === u.id).map((x) => outboxAsRequest(x, u.name)),
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
      await loadCrew()
      if (hasRole(u, RoleType.MASTER) || hasRole(u, RoleType.ADMIN)) {
        const filter = { route: null, station: null, from: null, to: null, states: [TripStatus.IN_PROGRESS] }
        depotTrips.value = (await loadCached('depot-trips', () => fetchTrips(filter, null))).data
      }
    })
  } catch (e) {
    error.value = shownError(e)
  }
}

// формирование показанного рейса — начальнику поезда этого рейса
async function loadCrew() {
  const shown = trip.value
  crew.value = null
  if (shown?.crewDutyType !== CrewDuty.LNP) return
  crew.value = (await loadCached(`crew:${shown.id}`, () => fetchTripCrew(shown.id))).data
}

// переключили рейс — формирование другого рейса
watch(
  () => trip.value?.id,
  () => void loadCrew().catch((e: unknown) => (error.value = shownError(e))),
)

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
  gap: 0.8rem;
  border: 0.0625rem solid $zs-border;
  border-radius: 0.4rem;
  background: #fff;
}

.today__trip-head {
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
}

.today__trip-badges {
  display: flex;
  font-size: 0.8rem;
}

.today__trip-name {
  font-size: 0.9rem;
  font-weight: 600;
  line-height: 1.3;
}

.today__tiles {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0.6rem;
}

.today__tile {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 0.1rem;
  border: 0.0625rem solid transparent;
  border-radius: 0.3rem;
  background: $zs-row-selected-bg;
  color: inherit;
  font: inherit;
  text-align: left;

  // плитка, по которой сейчас отмечается отправление или прибытие. Недоступную плитку не делаем disabled:
  // Quasar глобально бледнит всё с атрибутом disabled, а текст отметки должен оставаться обычным
  &--action {
    border-color: $primary;
    cursor: pointer;
  }
}

.today__coupling {
  display: flex;
  gap: 0.5rem;
  border-top: 0.0625rem solid $zs-border;
}

.today__coupling-link {
  flex: 1;
  border: 0.0625rem solid $primary;
  border-radius: 0.3rem;
  color: $primary;
  font-size: 0.9rem;
  font-weight: 600;
  text-align: center;
  text-decoration: none;
}

.today__tile-key {
  color: $zs-text-muted;
  font-size: 0.8rem;
}

.today__tile-value {
  font-size: 1.1rem;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

.today__crew {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.today__crew-card {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 0.8rem;
  border: 0.0625rem solid $zs-border;
  border-radius: 0.4rem;
  background: #fff;
}

.today__crew-link {
  flex: none;
}

.today__crew-attrs {
  display: grid;
  flex: 1;
  grid-template-columns: auto 1fr;
  gap: 0.4rem 0.8rem;
  align-items: center;
  min-width: 0;
  font-size: 0.9rem;
}

.today__norm {
  flex: none;
  border-radius: 62.5rem;
  background: #fbe9cf;
  color: #8a4b05;
  font-size: 0.8rem;

  &--ok {
    background: #dff0e2;
    color: #1d5e2c;
  }
}

.today__error {
  border-radius: 0.3rem;
  background: $zs-error-bg;
  color: $zs-error-fg;
  font-size: 0.8rem;
}

.today__tile-delay {
  color: $zs-text-muted;
  font-size: 0.8rem;
}

.today__switch {
  align-self: flex-end;
}

.today__cancel-mark {
  border: 0.0625rem solid $zs-error-fg;
  border-radius: 0.3rem;
  background: #fff;
  color: $zs-error-fg;
  font: inherit;
  font-weight: 600;
  cursor: pointer;
}
</style>
