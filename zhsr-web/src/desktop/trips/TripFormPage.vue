<template>
  <DesktopFrame v-model:floating="floating" :back="back" :right-open="true">
    <template #header>
      <h1 class="desktop-frame__title">{{ t('Формирование рейса') }}</h1>
      <!-- действия рейса в пути и после прибытия: видны все; нет прав или не тот статус — неактивна, причина рядом -->
      <div v-if="crew && !planned && headerButtons.length > 0" class="desktop-frame__actions">
        <template v-for="b in headerButtons" :key="b.label">
          <q-btn
            unelevated
            no-caps
            color="primary"
            :outline="!b.primary"
            :class="{ 'zs-soft-border': !b.primary }"
            :disable="!!b.reason || running"
            :title="b.reason || undefined"
            :label="t(b.label)"
            @click="b.run"
          />
        </template>
      </div>
    </template>

    <ErrorBox v-if="error" :error="error" :retry="crew ? null : load" class="trip-form__error q-mb-md" />
    <LoadSpinner v-if="busy.active.value" />

    <div v-if="crew && norm" class="trip-form q-pt-none q-px-none q-pb-md">
      <div class="trip-form__caption">
        {{ t('Поезд и бригада') }}
      </div>
      <div class="trip-form__train">
        <span>{{ t('Поезд') }}</span>
        <span class="trip-form__staff-name" :class="{ 'trip-form__bad': !crew.trip.train }">
          {{ crew.trip.train ? crew.trip.train.name : t('не назначен') }}
        </span>
        <button
          v-if="crew.trip.train"
          type="button"
          class="trip-form__link q-pa-none"
          @click="openTrain(crew.trip.train.id)"
        >
          {{ t('Состав поезда') }} →
        </button>
        <q-btn
          v-else
          unelevated
          outline
          no-caps
          color="primary"
          class="zs-soft-border"
          :disable="!!editReason"
          :title="editReason || undefined"
          @click="chooseTrain"
        >
          {{ t('Выбрать поезд') }}
        </q-btn>
      </div>

      <div class="trip-form__staff">
        <label v-for="d in duties" :key="d.duty" class="trip-form__duty">
          <span>{{ t(d.title) }}</span>
          <CbList
            v-if="canEdit"
            single
            no-search
            placeholder="Не назначен"
            style="width: 14rem"
            :model-value="d.member?.usr.id ?? null"
            :values="d.choices"
            :source="staffLoad"
            @update:model-value="setDuty(d.duty, $event as number | null)"
          >
            <!-- занятый в пересекающемся рейсе виден с пометкой; назначить его не даст сервер -->
            <template #option="{ item }">
              <div class="trip-form__candidate">
                <span>{{ item.text }}</span>
                <span v-if="item.data?.busy" class="trip-form__busy">{{ item.data.busy }}</span>
              </div>
            </template>
          </CbList>
          <span v-else class="trip-form__staff-name" :class="{ 'trip-form__bad': !d.member }">
            {{ d.member ? userName(d.member.usr) : t('не назначен') }}
          </span>
        </label>
      </div>

      <div class="trip-form__pool q-py-sm q-px-md">
        <span class="trip-form__pool-title">{{ t('Нераспределённые проводники') }}</span>
        <div class="trip-form__chips">
          <span v-for="m in norm.pool" :key="m.usr.id" class="trip-form__chip q-py-none q-px-sm">
            {{ label(m) }}
            <button
              type="button"
              class="trip-form__chip-del q-pa-none"
              :title="crewReason || t('Убрать из бригады')"
              :disabled="!!crewReason"
              @click="removeConductor(m)"
            >
              ✕
            </button>
          </span>
          <span v-if="norm.pool.length === 0" class="trip-form__muted">{{ t('Все проводники распределены') }}</span>
        </div>
        <q-btn
          unelevated
          outline
          no-caps
          color="primary"
          class="zs-soft-border"
          :disable="!!crewReason"
          :title="crewReason || undefined"
          @click="pickConductors"
        >
          {{ t('Проводники…') }}
        </q-btn>
      </div>

      <div class="trip-form__table">
        <div class="trip-form__row trip-form__row--head q-py-sm q-px-md">
          <span>{{ t('Поз.') }}</span>
          <span>{{ t('Вагон') }}</span>
          <span>{{ t('Где сейчас') }}</span>
          <span>{{ t('Проводник') }}</span>
          <span />
        </div>
        <div
          v-for="({ cw, w }, i) in wagonRows"
          :key="cw.id"
          class="trip-form__row q-py-sm q-px-md"
          :class="{ 'trip-form__row--off': !cw.riding }"
        >
          <span>{{ i + 1 }}</span>
          <span>
            <span class="zs-mono trip-form__number">{{ cw.number }}</span>
            <span class="trip-form__muted trip-form__block">{{ dictManager.getValue('WagonType', cw.wagonType) }}</span>
          </span>
          <span :class="{ 'trip-form__warn': isAway(cw) }">
            {{ where(cw) }}
            <span v-if="isAway(cw)" class="trip-form__block">{{ t('не на станции отправления') }}</span>
            <template v-if="isAway(cw)">
              <button
                type="button"
                class="trip-form__link q-pa-none"
                :disabled="!!returnReason(cw)"
                :title="returnReason(cw) || undefined"
                @click="returnWagon(cw)"
              >
                {{ t('Оформить передачу') }} →
              </button>
            </template>
            <span class="trip-form__changes">
              <ChangeNote v-if="cw.attachPlan" :change="cw.attachPlan" @mark="markPlan" @cancel="cancelPlan" />
              <ChangeNote
                v-else-if="cw.attached && cw.attached.trip?.id === tripId"
                :change="cw.attached"
                undoable
                @undo="undo"
              />
              <ChangeNote v-if="cw.detachPlan" :change="cw.detachPlan" @mark="markPlan" @cancel="cancelPlan" />
              <ChangeNote
                v-else-if="cw.detached && cw.detached.trip?.id === tripId"
                :change="cw.detached"
                undoable
                @undo="undo"
              />
            </span>
          </span>
          <span>
            <!-- проводники — у вагонов, которые едут в поезде -->
            <template v-if="!w" />
            <template v-else-if="canAssign && w.passenger">
              <span class="trip-form__slots">
                <CbList
                  v-for="slot in [0, 1]"
                  :key="slot"
                  single
                  no-search
                  placeholder="Не назначен"
                  class="trip-form__slot"
                  :class="{ 'trip-form__slot--bad': w.notAdjacent && !!w.conductors[slot] }"
                  :model-value="w.conductors[slot]?.usr.id ?? null"
                  :values="slotChoices(w, slot)"
                  @update:model-value="onSlot(w, slot, $event as number | null)"
                />
              </span>
              <span v-if="w.notAdjacent" class="trip-form__bad trip-form__block">
                {{ t('назначен на несоседние вагоны') }}
              </span>
            </template>
            <span v-else :class="{ 'trip-form__muted': !w.passenger }">{{ conductorsText(w) }}</span>
          </span>
          <span class="trip-form__moves">
            <button
              type="button"
              class="trip-form__move q-pa-none"
              :disabled="!!editReason || i === 0"
              :title="editReason || t('Выше')"
              @click="moveWagon(i, -1)"
            >
              ↑
            </button>
            <button
              type="button"
              class="trip-form__move q-pa-none"
              :disabled="!!editReason || i === crew.wagons.length - 1"
              :title="editReason || t('Ниже')"
              @click="moveWagon(i, 1)"
            >
              ↓
            </button>
            <q-btn
              unelevated
              outline
              no-caps
              color="primary"
              class="zs-soft-border"
              :disable="!!detachReason(cw)"
              :title="detachReason(cw) || undefined"
              @click="detachWagon(cw)"
            >
              {{ t('Отцепить') }}
            </q-btn>
          </span>
        </div>
        <div v-if="crew.wagons.length === 0" class="trip-form__empty q-pa-md">
          {{ crew.trip.train ? t('В поезде нет вагонов') : t('Поезд не назначен') }}
        </div>
      </div>
      <div class="trip-form__add">
        <q-btn
          unelevated
          outline
          no-caps
          color="primary"
          class="zs-soft-border"
          :disable="!!attachReason"
          :title="attachReason || undefined"
          @click="addWagons"
        >
          {{ t('Добавить вагоны') }}
        </q-btn>
      </div>
      <div class="trip-form__muted">
        {{
          planned
            ? t(
                'Вагоны прицепляются к поезду фактом или планом; на остановках рейса — только планом. Формирование поезда не меняет расположение вагонов.',
              )
            : t('После отправления поезд меняется прицепкой и отцепкой в рейсе.')
        }}
      </div>
    </div>

    <div v-if="crew && crew.changes.length > 0" class="trip-form">
      <div class="trip-form__caption">{{ t('Прицепки и отцепки рейса') }}</div>
      <div class="trip-form__table">
        <div v-for="c in crew.changes" :key="c.id" class="trip-form__act q-py-sm q-px-md">
          <span class="zs-mono">{{ c.number }}</span>
          <ChangeNote :change="c" undoable @mark="markPlan" @cancel="cancelPlan" @undo="undo" />
          <span class="trip-form__muted">{{ c.comment }}</span>
        </div>
      </div>
    </div>

    <template #right>
      <SidePanel
        :empty="crew ? '' : ' '"
        :title="crew ? t('Поезд № {train}', { train: crew.trip.trainNumber }) : ''"
        :subtitle="crew?.trip.name ?? ''"
        :closable="false"
      >
        <template v-if="crew && norm">
          <SidePanelFields>
            <span>{{ t('Поезд') }}</span>
            <span>{{ crew.trip.train?.name ?? t('не назначен') }}</span>
            <span>{{ t('Вид маршрута') }}</span>
            <span>{{ dictManager.getValue('TransportType', crew.trip.transportType) }}</span>
            <span>{{ t('Время в пути') }}</span>
            <span>{{ formatHm(crew.trip.duration) }}</span>
            <TripTimes
              :planned-dt="crew.trip.plannedDt"
              :departed-dt="crew.trip.departedDt"
              :arrived-dt="crew.trip.arrivedDt"
              :duration="crew.trip.duration"
            />
            <span>{{ t('Состояние') }}</span>
            <span><DictBadge :id="crew.trip.tripStatusType" dict="TripState" /></span>
            <!-- после прибытия ЛНП назначает ответственного за вагоны поезда — как исполнителя заявки; до назначения
                 отвечают проводники -->
            <template v-if="crew.trip.tripStatusType === TripStatus.DONE">
              <span>{{ t('Ответственный за вагоны') }}</span>
              <span>
                {{ responsibleText(crew.trip) }}
                <SoftLink
                  v-if="canMark"
                  :text="crew.trip.responsibleAssigned ? 'Переназначить' : 'Назначить'"
                  :disabled="running"
                  class="q-ml-sm"
                  @click="assignResponsible"
                />
                <span v-else class="trip-form__muted trip-form__block">{{ t(MARK_REASON) }}</span>
              </span>
            </template>
          </SidePanelFields>

          <template v-if="planned">
            <div class="trip-form__caption">{{ t('Готовность') }}</div>
            <div v-for="c in readiness" :key="c.title" class="trip-form__ready">
              <span class="trip-form__ready-icon" :class="{ 'trip-form__ready-icon--ok': c.ok }">
                {{ c.ok ? '✓' : '✕' }}
              </span>
              <span>
                <span class="trip-form__ready-title">{{ c.title }}</span>
                <span class="trip-form__muted trip-form__block">{{ c.detail }}</span>
              </span>
            </div>
            <div v-if="ready" class="trip-form__verdict">{{ t('Поезд готов к отправлению') }}</div>
            <q-btn
              unelevated
              no-caps
              color="primary"
              :disable="!canMark || !ready || running"
              :title="canMark ? undefined : t(MARK_REASON)"
              :label="t('Отметить отправление')"
              @click="mark(true)"
            />
          </template>
        </template>
      </SidePanel>
    </template>
  </DesktopFrame>
</template>

<script setup lang="ts">
import { Notify } from 'quasar'
import { computed, ref, shallowRef, watch } from 'vue'
import { useRouter } from 'vue-router'

import type { TrainCard, TrainChange } from '@/api/trains'
import { attachWagons, cancelChangePlan, detachWagons, orderTrain } from '@/api/trains'
import type { CrewMember, CrewWagon, StaffCandidate, TripCrew, TripRow } from '@/api/trips'
import {
  assignTripResponsible,
  cancelArrival,
  cancelDeparture,
  fetchStaffCandidates,
  fetchTripCrew,
  setConductorWagons,
  setTripConductors,
  setTripDuty,
} from '@/api/trips'
import { t } from '@/boot/i18n'
import { formatHm } from '@/desktop/routes/duration'
import { provideBusy } from '@/shared/busy'
import CbList from '@/shared/components/choice/CbList.vue'
import { openDialog } from '@/shared/components/dialog/openDialog'
import SoftLink from '@/shared/components/form/SoftLink.vue'
import ResponsibleAssignDialog from '../wagons/ResponsibleAssignDialog.vue'
import DictBadge from '@/shared/components/DictBadge.vue'
import ErrorBox from '@/shared/components/ErrorBox.vue'
import LoadSpinner from '@/shared/components/LoadSpinner.vue'
import { CrewDuty, LocationType, RoleType, TripStatus } from '@/shared/constants/systemDicts'
import { TrainWagonChangeType } from '@/shared/constants/TrainWagonChange'
import { dictManager } from '@/shared/dict/DictManager'
import type { ShownError } from '@/shared/errors'
import { shownError } from '@/shared/errors'
import { formatDateTime } from '@/shared/format'
import { useLoad } from '@/shared/loadable'
import type { WagonNorm } from '@/shared/trips/crewNorm'
import { crewNorm, normDone, slotOptions } from '@/shared/trips/crewNorm'
import { currentUser, hasRole, loadCurrentUser, userName } from '@/shared/user'
import { placeText } from '@/shared/wagonPlace'
import DesktopFrame from '../frame/DesktopFrame.vue'
import SidePanel from '../frame/SidePanel.vue'
import SidePanelFields from '../frame/SidePanelFields.vue'
import CrewPickDialog from './CrewPickDialog.vue'
import type { AttachChoice, ChangeChoice } from '../trains/attach'
import { changeBody, tripStops } from '../trains/attach'
import AttachWagonsDialog from '../trains/AttachWagonsDialog.vue'
import ChangeNote from '../trains/ChangeNote.vue'
import ChangeUndoDialog from '../trains/ChangeUndoDialog.vue'
import DetachDialog from '../trains/DetachDialog.vue'
import MarkChangeDialog from '../trains/MarkChangeDialog.vue'
import TripMarkDialog from './TripMarkDialog.vue'
import TripNewDialog from './TripNewDialog.vue'
import { tripRefText } from '@/shared/trips/tripRefText'
import TripTimes from './TripTimes.vue'
import { markEditable, useNow } from '@/shared/trips/tripTimes'

/**
 * Формирование рейса (Ф-РЕЙ-02, Ф-РЕЙ-06): слева поезд рейса и бригада, справа закреплённая панель рейса с готовностью
 * и отметкой отправления. Вагоны поезда на время рейса — по факту и по плану: прицепленные и отцепляемые на остановках
 * рейса показаны с пометкой, в норму проводников идут вагоны, которые едут на отправлении. В рейсе в статусе «План»
 * вагоны поезда, начальника поезда и ПЭМ меняют планировщик вагонов и администратор, список проводников — планировщик
 * проводников и администратор; проводников по вагонам распределяют планировщик проводников и начальник поезда этого
 * рейса. Начальник поезда отмечает плановые прицепки и отцепки, отправление и прибытие. Остальные видят экран без
 * правки. Проверки — на сервере; здесь только доступность кнопок.
 */
const props = defineProps<{ id: string }>()

const router = useRouter()
const tripId = computed(() => Number(props.id))
// общее ожидание страницы: спиннер на месте содержимого — на всю цепочку загрузок
const busy = provideBusy()
const floating = ref(false)

const crew = shallowRef<TripCrew | null>(null)
const error = ref<ShownError | null>(null)
const running = ref(false)

const norm = computed(() => (crew.value ? crewNorm(crew.value) : null))
const planned = computed(() => crew.value?.trip.tripStatusType === TripStatus.PLANNED)
const inProgress = computed(() => crew.value?.trip.tripStatusType === TripStatus.IN_PROGRESS)

const isAdmin = computed(() => hasRole(currentUser.value, RoleType.ADMIN))
// состав, начальник поезда и ПЭМ — планировщик вагонов
const canEdit = computed(() => planned.value && (isAdmin.value || hasRole(currentUser.value, RoleType.WAGON_PLANNER)))
// список проводников рейса — планировщик проводников
const canEditCrew = computed(
  () => planned.value && (isAdmin.value || hasRole(currentUser.value, RoleType.CREW_PLANNER)),
)
/** Почему вагоны поезда, ЛНП и ПЭМ не поменять: нет прав, рейс не в статусе «План» или поезд не назначен. */
const editReason = computed(() =>
  why(
    isAdmin.value || hasRole(currentUser.value, RoleType.WAGON_PLANNER),
    PLANNER_REASON,
    statusReason(TripStatus.PLANNED) || (crew.value?.trip.train ? '' : t('Поезд рейса не назначен')),
  ),
)
/** Почему вагоны не добавить: до отправления — как editReason; в пути — только план планировщика. */
const attachReason = computed(() =>
  why(
    isAdmin.value || hasRole(currentUser.value, RoleType.WAGON_PLANNER),
    PLANNER_REASON,
    statusReason(TripStatus.PLANNED, TripStatus.IN_PROGRESS) ||
      (crew.value?.trip.train ? '' : t('Поезд рейса не назначен')),
  ),
)
/** Почему список проводников рейса не поменять: нет прав или рейс не в статусе «План»; пусто — можно. */
const crewReason = computed(() =>
  why(
    isAdmin.value || hasRole(currentUser.value, RoleType.CREW_PLANNER),
    CREW_PLANNER_REASON,
    statusReason(TripStatus.PLANNED),
  ),
)
const isChief = computed(
  () => !!crew.value?.crew.some((m) => m.crewDutyType === CrewDuty.LNP && m.usr.id === currentUser.value?.id),
)
const canMark = computed(() => isChief.value || isAdmin.value)

// исправить или отменить отметку можно 10 минут после неё
const now = useNow()
const fixDeparture = computed(
  () => canMark.value && inProgress.value && markEditable(crew.value?.trip.departureMarkedDt, now.value),
)
const fixArrival = computed(
  () =>
    canMark.value &&
    crew.value?.trip.tripStatusType === TripStatus.DONE &&
    markEditable(crew.value.trip.arrivalMarkedDt, now.value),
)
const canCouple = computed(() => canMark.value || hasRole(currentUser.value, RoleType.MASTER))

/** Почему действие недоступно в текущем статусе рейса; пусто — доступно. */
function statusReason(...allowed: number[]): string {
  const s = crew.value?.trip.tripStatusType
  if (s !== undefined && allowed.includes(s)) return ''
  return t('Недоступно в статусе «{status}»', { status: dictManager.getValue('TripState', s) })
}
const FIX_REASON = 'Исправить можно в течение 10 минут после отметки'
const CANCEL_REASON = 'Отменить можно в течение 10 минут после отметки'
const MARK_REASON = 'Отмечает ЛНП этого рейса'
const PLANNER_REASON = 'Вагоны поезда, ЛНП и ПЭМ меняет планировщик вагонов'
const CREW_PLANNER_REASON = 'Проводников рейса назначает планировщик проводников'
const COUPLE_REASON = 'Прицепку и отцепку в рейсе оформляет ЛНП этого рейса или мастер депо'
const TRANSFER_REASON = 'Передачу оформляет мастер депо или приёмщик'

/** Почему кнопка недоступна: сначала нет прав (rights — права есть, reason — текст), затем status; пусто — доступна. */
const why = (rights: boolean, reason: string, status: string) => (rights ? status : t(reason))

/** Кнопки в шапке: видны все; reason — почему недоступно сейчас. */
const headerButtons = computed(() => [
  {
    label: 'Отцепка',
    primary: false,
    reason: why(canCouple.value, COUPLE_REASON, statusReason(TripStatus.IN_PROGRESS)),
    run: () => couple('detach'),
  },
  {
    label: 'Прицепка',
    primary: false,
    reason: why(canCouple.value, COUPLE_REASON, statusReason(TripStatus.IN_PROGRESS)),
    run: () => couple('attach'),
  },
  {
    label: 'Отметить прицепку',
    primary: false,
    reason: why(
      canMark.value,
      MARK_REASON,
      statusReason(TripStatus.IN_PROGRESS) || (plannedChanges.value.length ? '' : t('Плановых прицепок и отцепок нет')),
    ),
    run: () => void markNext(),
  },
  {
    label: 'Отметить прибытие',
    primary: true,
    reason: why(canMark.value, MARK_REASON, statusReason(TripStatus.IN_PROGRESS)),
    run: () => mark(false),
  },
  {
    label: 'Исправить отправление',
    primary: true,
    reason: why(
      canMark.value,
      MARK_REASON,
      statusReason(TripStatus.IN_PROGRESS) || (fixDeparture.value ? '' : t(FIX_REASON)),
    ),
    run: () => mark(true, true),
  },
  {
    label: 'Отменить отправление',
    primary: false,
    reason: why(
      canMark.value,
      MARK_REASON,
      statusReason(TripStatus.IN_PROGRESS) || (fixDeparture.value ? '' : t(CANCEL_REASON)),
    ),
    run: () => void cancelMark(true),
  },
  {
    label: 'Исправить прибытие',
    primary: true,
    reason: why(canMark.value, MARK_REASON, statusReason(TripStatus.DONE) || (fixArrival.value ? '' : t(FIX_REASON))),
    run: () => mark(false, true),
  },
  {
    label: 'Отменить прибытие',
    primary: false,
    reason: why(
      canMark.value,
      MARK_REASON,
      statusReason(TripStatus.DONE) || (fixArrival.value ? '' : t(CANCEL_REASON)),
    ),
    run: () => void cancelMark(false),
  },
])
const canAssign = computed(
  () =>
    canEditCrew.value || (inProgress.value && isAdmin.value) || ((planned.value || inProgress.value) && isChief.value),
)

const back = () => void router.push({ name: 'trips' })
const label = (m: CrewMember) => (m.senior ? `${userName(m.usr)} • ${t('ст.')}` : userName(m.usr))

async function load() {
  error.value = null
  try {
    await busy.run(async () => {
      const [data] = await Promise.all([
        fetchTripCrew(tripId.value),
        dictManager.prepareDicts('Station', 'Depot', 'Yard', 'WagonType', 'TransportType'),
        loadCurrentUser(),
      ])
      crew.value = data
    })
  } catch (e) {
    error.value = shownError(e)
  }
}
void load()

// варианты начальника поезда и ПЭМ — свои выпадающие списки, страница их не ждёт; ошибка — в самом списке
const chiefs = shallowRef<StaffCandidate[]>([])
const pems = shallowRef<StaffCandidate[]>([])
const staffLoad = useLoad(async () => {
  const [lnp, pem] = await Promise.all([
    fetchStaffCandidates(tripId.value, CrewDuty.LNP),
    fetchStaffCandidates(tripId.value, CrewDuty.PEM),
  ])
  chiefs.value = lnp
  pems.value = pem
})
watch(
  canEdit,
  (edit) => {
    if (edit) void staffLoad.run()
  },
  { immediate: true },
)

/** Занятость кандидата: «занят на рейсе 2/1 Алматы — Астана до 08.11 16:00»; свободен — пусто. */
function busyText(c: StaffCandidate): string {
  if (!c.busyUntil) return ''
  return t('занят на рейсе {trip} до {until}', {
    trip: c.busyTrip ? tripRefText(c.busyTrip) : '',
    until: formatDateTime(c.busyUntil),
  })
}

const duties = computed(() => {
  const line = (duty: number, title: string, list: StaffCandidate[]) => {
    const member = crew.value?.crew.find((m) => m.crewDutyType === duty) ?? null
    const choices: { id: number; text: string; data?: Record<string, unknown> }[] = list.map((c) => ({
      id: c.id,
      text: userName(c),
      data: { busy: busyText(c) },
    }))
    // назначенный виден и если сейчас недоступен для выбора
    if (member && !choices.some((c) => c.id === member.usr.id))
      choices.unshift({ id: member.usr.id, text: userName(member.usr) })
    return { duty, title, member, choices }
  }
  return [line(CrewDuty.LNP, 'Начальник поезда', chiefs.value), line(CrewDuty.PEM, 'ПЭМ', pems.value)]
})

// --- правка: ответ сервера — новый состав и бригада; ошибка — над таблицей ---

async function apply(work: () => Promise<TripCrew>) {
  error.value = null
  running.value = true
  try {
    crew.value = await work()
  } catch (e) {
    error.value = shownError(e)
  } finally {
    running.value = false
  }
}

const setDuty = (duty: number, user: number | null) => apply(() => setTripDuty(tripId.value, duty, user))

/** Ответственный за вагоны прибывшего рейса словами: сотрудник, «не назначен» или «проводники вагонов». */
function responsibleText(trip: TripCrew['trip']): string {
  if (!trip.responsibleAssigned) return t('проводники вагонов')
  return trip.responsibleUsr ? userName(trip.responsibleUsr) : t('не назначен')
}

// ответственный за вагоны прибывшего рейса — во всплывающем окне, как исполнитель заявки
async function assignResponsible() {
  const trip = crew.value!.trip
  const result = await openDialog<{ usr: number | null }>(ResponsibleAssignDialog, {
    station: null,
    trip: trip.id,
    current: trip.responsibleUsr,
    reassign: trip.responsibleAssigned,
  })
  if (result) await apply(() => assignTripResponsible(tripId.value, result.usr))
}

const conductors = () => crew.value!.crew.filter((m) => m.crewDutyType === CrewDuty.CONDUCTOR).map((m) => m.usr.id)

function removeConductor(m: CrewMember) {
  void apply(() =>
    setTripConductors(
      tripId.value,
      conductors().filter((u) => u !== m.usr.id),
    ),
  )
}

async function pickConductors() {
  const trip = crew.value!.trip
  const saved = await openDialog<boolean>(CrewPickDialog, {
    tripId: trip.id,
    tripName: `${trip.trainNumber} • ${trip.name}`,
    station: dictManager.getValue('Station', trip.departureStation),
  })
  if (saved) await load()
}

const slotChoices = (w: WagonNorm, slot: number) =>
  slotOptions(crew.value!, w, slot, label).map((o) => ({ id: o.user, text: o.label }))

function onSlot(w: WagonNorm, slot: number, user: number | null) {
  const prev = w.conductors[slot]
  if (prev?.usr.id === user) return
  const next = crew.value!.crew.find((m) => m.usr.id === user)
  void apply(async () => {
    let result = crew.value!
    // прежний проводник места уходит с этого вагона
    if (prev) {
      result = await setConductorWagons(
        tripId.value,
        prev.usr.id,
        prev.wagons.filter((x) => x !== w.number),
      )
    }
    // новый — на этот вагон; если у него уже есть соседний вагон, вагонов становится два
    if (next) result = await setConductorWagons(tripId.value, next.usr.id, [...next.wagons, w.number])
    return result
  })
}

// --- поезд рейса: операции с поездом возвращают поезд, рейс перечитывается ---

async function trainApply(work: () => Promise<TrainCard>) {
  error.value = null
  running.value = true
  try {
    await work()
    crew.value = await fetchTripCrew(tripId.value)
  } catch (e) {
    error.value = shownError(e)
  } finally {
    running.value = false
  }
}

const trainId = () => crew.value!.trip.train!.id

function moveWagon(i: number, step: number) {
  const list = crew.value!.wagons.map((w) => w.number)
  const [wagon] = list.splice(i, 1)
  list.splice(i + step, 0, wagon!)
  void trainApply(() => orderTrain(trainId(), list))
}

/** Почему вагон не отцепить: нет прав, рейс закончен, вагон уже отцеплен или отцепка запланирована. */
function detachReason(w: CrewWagon): string {
  return attachReason.value || (w.dend ? t('Вагон отцеплен') : w.detachPlan ? t('Отцепка уже запланирована') : '')
}

async function detachWagon(w: CrewWagon) {
  const trip = crew.value!.trip
  // до отправления вагон отцепляется фактом на своей станции; в пути — только планом на остановке
  const choice = await openDialog<ChangeChoice>(DetachDialog, {
    number: w.number,
    stops: planned.value ? tripStops(trip) : tripStops(trip).slice(1),
    factAllowed: planned.value && !!w.dbeg,
  })
  if (!choice) return
  await trainApply(() => detachWagons(trainId(), changeBody([w.number], choice, trip.id)))
}

async function addWagons() {
  const trip = crew.value!.trip
  const stops = tripStops(trip)
  const choice = await openDialog<AttachChoice>(AttachWagonsDialog, {
    trainId: trainId(),
    station: trip.departureStation,
    at: trip.plannedDt,
    stops: planned.value ? stops : stops.slice(1),
    factStop: planned.value ? (stops[0] ?? null) : null,
    factAllowed: planned.value,
  })
  if (!choice) return
  await trainApply(() => attachWagons(trainId(), changeBody(choice.numbers, choice, trip.id)))
}

async function chooseTrain() {
  const trip = crew.value!.trip
  const saved = await openDialog<TripRow>(TripNewDialog, {
    trip: { id: trip.id, route: trip.route, plannedDt: trip.plannedDt, train: null },
  })
  if (saved) await load()
}

const openTrain = (id: number) => void router.push({ name: 'train-form', params: { id } })

// --- плановые прицепки и отцепки рейса ---

const plannedChanges = computed(() => (crew.value?.changes ?? []).filter((c) => c.factDt === null))

async function markPlan(change: TrainChange) {
  const done = await openDialog<TrainCard>(MarkChangeDialog, { change, inProgress: inProgress.value })
  if (done) await load()
}

/** «Отметить прицепку» в шапке: ближайшая по времени плановая прицепка или отцепка рейса. */
async function markNext() {
  const next = plannedChanges.value[0]
  if (next) await markPlan(next)
}

function cancelPlan(change: TrainChange) {
  void trainApply(() => cancelChangePlan(change.id))
}

async function undo(change: TrainChange) {
  const done = await openDialog<true>(ChangeUndoDialog, {
    changeId: change.id,
    attach: change.changeType === TrainWagonChangeType.ATTACH,
    number: change.number,
  })
  if (done) await load()
}

// --- показ ---

const couple = (kind: string) => void router.push({ name: 'trip-coupling', params: { id: props.id, kind } })

/** Строки таблицы: вагон поезда и его норма проводников — норма есть только у вагонов, которые едут в поезде. */
const wagonRows = computed(() =>
  (crew.value?.wagons ?? []).map((cw) => ({
    cw,
    w: norm.value?.wagons.find((n) => n.number === cw.number) ?? null,
  })),
)

const atStation = (w: CrewWagon) =>
  w.locationType === LocationType.STATION && w.place === crew.value!.trip.departureStation
const isAway = (w: CrewWagon) => planned.value && w.riding && !atStation(w)

// передачу на станцию оформляют мастер депо, приёмщик и администратор
const canTransfer = computed(() =>
  [RoleType.ADMIN, RoleType.MASTER, RoleType.RECEIVER].some((r) => hasRole(currentUser.value, r)),
)

/**
 * Почему передачу вагона не оформить: нет прав; передать на станцию можно только из депо или парка отстоя; пусто —
 * можно.
 */
function returnReason(w: CrewWagon): string {
  if (!canTransfer.value) return t(TRANSFER_REASON)
  if (w.locationType === LocationType.DEPOT || w.locationType === LocationType.YARD) return ''
  return t('Передать на станцию можно только из депо или парка отстоя')
}

function returnWagon(w: CrewWagon) {
  void router.push({
    name: 'transfer',
    query: {
      from: w.locationType === LocationType.DEPOT ? `d${w.place}` : `y${w.place}`,
      to: `s${crew.value!.trip.departureStation}`,
      wagons: w.number,
    },
  })
}

function where(w: CrewWagon): string {
  if (w.locationType === LocationType.TRIP && w.place === crew.value!.trip.id) return t('В поезде рейса')
  return placeText(w.locationType, w.place, w.trip ? tripRefText(w.trip) : null)
}

function conductorsText(w: WagonNorm): string {
  if (!w.passenger) return t('не требуется')
  if (w.conductors.length === 0) return t('не назначены')
  return w.conductors.map(label).join(', ')
}

const riding = computed(() => (crew.value?.wagons ?? []).filter((w) => w.riding))
const wagonsReady = computed(() => riding.value.length > 0 && riding.value.every(atStation))

const readiness = computed(() => {
  const n = norm.value!
  const all = riding.value
  const pending = crew.value!.pending
  const station = dictManager.getValue('Station', crew.value!.trip.departureStation)
  const chief = crew.value!.crew.find((m) => m.crewDutyType === CrewDuty.LNP)
  const pem = crew.value!.crew.find((m) => m.crewDutyType === CrewDuty.PEM)
  return [
    {
      ok: !!crew.value!.trip.train && pending.length === 0,
      title: t('Поезд назначен, планы до отправления отмечены'),
      detail: !crew.value!.trip.train
        ? t('поезд не назначен')
        : pending.length
          ? t('не отмечены: {list}', { list: pending.map((c) => c.number).join(', ') })
          : crew.value!.trip.train.name,
    },
    {
      ok: wagonsReady.value,
      title: t('Вагоны на станции отправления'),
      detail: all.length
        ? t('{a} из {b} на {station}', { a: all.filter(atStation).length, b: all.length, station })
        : t('в поезде нет вагонов'),
    },
    {
      ok: n.chiefAndPem,
      title: t('Начальник поезда и ПЭМ'),
      detail: `${t('ЛНП')} ${chief ? userName(chief.usr) : '—'} • ${t('ПЭМ')} ${pem ? userName(pem.usr) : '—'}`,
    },
    {
      ok: normDone(n),
      title: t('Проводники по норме'),
      detail:
        t('назначено {have} из {need}', { have: n.have, need: n.need }) +
        (n.notOk.length ? ` • ${t('не выполнена: {list}', { list: n.notOk.join(', ') })}` : ''),
    },
  ]
})
const ready = computed(() => readiness.value.every((c) => c.ok))

// отменить отметку — кнопкой в шапке, без окна, как на телефоне: 10 минут после отметки; ошибка — над таблицей
async function cancelMark(depart: boolean) {
  await apply(() => (depart ? cancelDeparture : cancelArrival)(tripId.value))
  if (error.value) return
  Notify.create({
    message: depart ? t('Отправление отменено') : t('Прибытие отменено'),
    type: 'positive',
    position: 'bottom',
  })
  void loadCurrentUser(true)
}

// отметить; fix — исправить время уже сделанной отметки или отменить её
async function mark(depart: boolean, fix = false) {
  const markedTime = depart ? crew.value?.trip.departedDt : crew.value?.trip.arrivedDt
  const result = await openDialog<{ crew: TripCrew; message: string }>(TripMarkDialog, {
    tripId: tripId.value,
    depart,
    fix: fix ? markedTime : null,
  })
  if (!result) return
  crew.value = result.crew
  Notify.create({ message: result.message, type: 'positive', position: 'bottom' })
  void loadCurrentUser(true)
}
</script>

<style scoped lang="scss">
.trip-form {
  display: flex;
  flex-direction: column;
  gap: 0.8rem;
  overflow-y: auto;
}

.trip-form__caption {
  color: $zs-text-muted;
  font-size: 0.9rem;
  font-weight: 600;
}

.trip-form__staff {
  display: flex;
  flex-wrap: wrap;
  gap: 0.8rem 1.8rem;
}

.trip-form__duty {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  color: $zs-text-muted;
  font-size: 0.8rem;
}

.trip-form__staff-name {
  color: $zs-text;
  font-weight: 600;
}

.trip-form__pool {
  display: flex;
  align-items: center;
  gap: 0.8rem;
  border: 0.0625rem solid $zs-border;
  border-radius: 0.3rem;
  background: $zs-bg-header;
  font-size: 0.8rem;
}

.trip-form__pool-title {
  flex: none;
  font-weight: 600;
}

.trip-form__chips {
  display: flex;
  flex: 1;
  flex-wrap: wrap;
  gap: 0.4rem;
  min-width: 0;
}

.trip-form__chip {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  border: 0.0625rem solid $zs-border;
  border-radius: 62.5rem;
  background: #fff;
}

.trip-form__chip-del {
  border: 0;
  background: none;
  color: $zs-text-muted;
  cursor: pointer;
}

.trip-form__table {
  border: 0.0625rem solid $zs-border;
  border-radius: 0.3rem;
  background: #fff;
}

.trip-form__row {
  display: grid;
  grid-template-columns: 2.5rem 8rem minmax(9rem, 1fr) minmax(18rem, 2fr) 12rem;
  align-items: center;
  gap: 0.8rem;
  border-top: 0.0625rem solid $zs-border-row;
  font-size: 0.8rem;

  &--head {
    border-top: 0;
    background: $zs-bg-header;
    color: $zs-text-muted;
    font-size: 0.8rem;
    font-weight: 600;
  }

  // вагон не едет в поезде: отцеплен или прицепляется в пути
  &--off {
    color: $zs-text-muted;
  }
}

.trip-form__train {
  display: flex;
  align-items: center;
  gap: 0.8rem;
  color: $zs-text-muted;
  font-size: 0.8rem;
}

.trip-form__changes {
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
}

.trip-form__add {
  display: flex;
  justify-content: flex-end;
}

.trip-form__number {
  font-weight: 700;
}

.trip-form__block {
  display: block;
}

.trip-form__muted {
  color: $zs-text-muted;
  font-size: 0.8rem;
}

.trip-form__warn {
  color: $zs-error-fg;
  font-weight: 600;
}

.trip-form__bad {
  color: $zs-error-fg;
}

.trip-form__slots {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0.4rem;
}

.trip-form__slot {
  min-width: 0;

  &--bad {
    outline: 0.0625rem solid $zs-error-fg;
  }
}

.trip-form__link {
  display: block;
  border: 0;
  background: none;
  color: $primary;
  font: inherit;
  font-size: 0.8rem;
  font-weight: 600;
  cursor: pointer;

  &:disabled {
    color: $zs-text-muted;
    cursor: default;
  }
}

.trip-form__moves {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 0.3rem;
}

.trip-form__move {
  width: 1.8rem;
  height: 1.8rem;
  border: 0.0625rem solid $zs-border-input;
  border-radius: 0.3rem;
  background: #fff;
  color: $zs-text;
  cursor: pointer;

  &:disabled {
    opacity: 0.35;
    cursor: default;
  }
}

.trip-form__empty {
  display: flex;
  align-items: center;
  gap: 0.8rem;
  color: $zs-text-muted;
  font-size: 0.8rem;
}

.trip-form__act {
  display: grid;
  grid-template-columns: 6rem minmax(16rem, 2fr) minmax(10rem, 1fr);
  gap: 0.8rem;
  border-top: 0.0625rem solid $zs-border-row;
  font-size: 0.8rem;

  &:first-child {
    border-top: 0;
  }
}

.trip-form__ready {
  display: flex;
  gap: 0.5rem;
  font-size: 0.8rem;
}

.trip-form__ready-icon {
  flex: none;
  color: $zs-error-fg;
  font-weight: 700;

  &--ok {
    color: $primary;
  }
}

.trip-form__ready-title {
  display: block;
  font-weight: 600;
}

.trip-form__verdict {
  color: $primary;
  font-size: 0.8rem;
  font-weight: 600;
}

.trip-form__candidate {
  display: flex;
  flex-direction: column;
}

// пометка о занятости кандидата — как предупреждение
.trip-form__busy {
  color: $zs-error-fg;
  font-size: 0.8rem;
}
</style>
