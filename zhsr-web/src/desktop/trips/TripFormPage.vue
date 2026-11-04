<template>
  <DesktopFrame v-model:floating="floating" :back="back" :right-open="true">
    <template #header>
      <h1 class="desktop-frame__title">{{ t('Формирование рейса') }}</h1>
      <div v-if="crew && inProgress" class="desktop-frame__actions">
        <template v-if="canCouple">
          <button type="button" class="trip-form__btn" @click="couple('detach')">{{ t('Отцепка') }}</button>
          <button type="button" class="trip-form__btn" @click="couple('attach')">{{ t('Прицепка') }}</button>
        </template>
        <button
          type="button"
          class="zs-btn-primary"
          :disabled="!canMark || running"
          :title="canMark ? '' : t('Начальник поезда этого рейса')"
          @click="mark(false)"
        >
          {{ t('Отметить прибытие') }}
        </button>
      </div>
    </template>

    <ErrorBox v-if="error" :error="error" :retry="crew ? null : load" class="trip-form__error" />
    <LoadSpinner v-if="busy.active.value" />

    <div v-if="crew && norm" class="trip-form">
      <div class="trip-form__caption">{{ t('Состав и бригада') }}</div>

      <div class="trip-form__staff">
        <label v-for="d in duties" :key="d.duty" class="trip-form__duty">
          <span>{{ t(d.title) }}</span>
          <CbList
            v-if="canEdit"
            single
            no-search
            placeholder="Не назначен"
            style="width: 14rem"
            :model-value="d.member?.usr ?? null"
            :values="d.choices"
            :source="staffLoad"
            @update:model-value="setDuty(d.duty, $event as number | null)"
          />
          <span v-else class="trip-form__staff-name" :class="{ 'trip-form__bad': !d.member }">
            {{ d.member?.name ?? t('не назначен') }}
          </span>
        </label>
      </div>

      <div v-if="canEdit" class="trip-form__pool">
        <span class="trip-form__pool-title">{{ t('Нераспределённые проводники') }}</span>
        <div class="trip-form__chips">
          <span v-for="m in norm.pool" :key="m.usr" class="trip-form__chip">
            {{ label(m) }}
            <button
              type="button"
              class="trip-form__chip-del"
              :title="t('Убрать из бригады')"
              @click="removeConductor(m)"
            >
              ✕
            </button>
          </span>
          <span v-if="norm.pool.length === 0" class="trip-form__muted">{{ t('Все проводники распределены') }}</span>
        </div>
        <button type="button" class="trip-form__btn" @click="pickConductors">{{ t('Проводники…') }}</button>
      </div>

      <div class="trip-form__table">
        <div class="trip-form__row trip-form__row--head">
          <span>{{ t('Поз.') }}</span>
          <span>{{ t('Вагон') }}</span>
          <span>{{ t('Где сейчас') }}</span>
          <span>{{ t('Проводник') }}</span>
          <span />
        </div>
        <div v-for="(w, i) in norm.wagons" :key="w.number" class="trip-form__row">
          <span>{{ w.pos }}</span>
          <span>
            <span class="zs-mono trip-form__number">{{ w.number }}</span>
            <span class="trip-form__muted trip-form__block">{{ dictManager.getValue('WagonType', w.type) }}</span>
          </span>
          <span :class="{ 'trip-form__warn': isAway(i) }">
            {{ where(i) }}
            <span v-if="isAway(i)" class="trip-form__block">{{ t('не на станции отправления') }}</span>
            <button v-if="canReturn(i)" type="button" class="trip-form__link" @click="returnWagon(i)">
              {{ t('Оформить передачу') }} →
            </button>
          </span>
          <span>
            <template v-if="canAssign && w.passenger">
              <span class="trip-form__slots">
                <CbList
                  v-for="slot in [0, 1]"
                  :key="slot"
                  single
                  no-search
                  placeholder="Не назначен"
                  class="trip-form__slot"
                  :class="{ 'trip-form__slot--bad': w.notAdjacent && !!w.conductors[slot] }"
                  :model-value="w.conductors[slot]?.usr ?? null"
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
          <span v-if="canEdit" class="trip-form__moves">
            <button
              type="button"
              class="trip-form__move"
              :disabled="i === 0"
              :title="t('Выше')"
              @click="moveWagon(i, -1)"
            >
              ↑
            </button>
            <button
              type="button"
              class="trip-form__move"
              :disabled="i === norm.wagons.length - 1"
              :title="t('Ниже')"
              @click="moveWagon(i, 1)"
            >
              ↓
            </button>
            <button type="button" class="trip-form__move" :title="t('Убрать из состава')" @click="removeWagon(i)">
              ✕
            </button>
            <button
              type="button"
              class="trip-form__btn"
              :title="t('Добавить вагоны после этого')"
              @click="addWagons(i + 1)"
            >
              {{ t('+ Вагон') }}
            </button>
          </span>
          <span v-else />
        </div>
        <div v-if="norm.wagons.length === 0" class="trip-form__empty">
          {{ t('Состав не сформирован') }}
          <button v-if="canEdit" type="button" class="trip-form__btn" @click="addWagons(0)">{{ t('+ Вагон') }}</button>
        </div>
      </div>
      <div class="trip-form__muted">
        {{
          planned
            ? t(
                'Состав и бригада меняются, пока рейс в статусе «План». Формирование состава не меняет расположение вагонов.',
              )
            : t('После отправления состав меняется только актами отцепки и прицепки.')
        }}
      </div>
    </div>

    <div v-if="crew && crew.acts.length > 0" class="trip-form">
      <div class="trip-form__caption">{{ t('Акты по рейсу') }}</div>
      <div class="trip-form__table">
        <div v-for="a in crew.acts" :key="a.number" class="trip-form__act">
          <span class="zs-mono">{{ a.number }}</span>
          <span>{{ actTitle(a) }}</span>
          <span class="zs-mono">{{ a.wagons.join(', ') }}</span>
          <span class="trip-form__muted">{{ formatDateTime(a.createdDt) }}</span>
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
            <span>{{ t('Вид маршрута') }}</span>
            <span>{{ dictManager.getValue('TransportType', crew.trip.transportType) }}</span>
            <span>{{ t('Время в пути') }}</span>
            <span>{{ formatHm(crew.trip.duration) }}</span>
            <span>{{ t('План') }}</span>
            <span>{{ formatDateTime(crew.trip.plannedDt) }}</span>
            <template v-if="crew.trip.departedDt">
              <span>{{ t('Отправление') }}</span>
              <span>{{ formatDateTime(crew.trip.departedDt) }}</span>
            </template>
            <span>{{ t('Прибытие') }}</span>
            <span>{{ formatDateTime(crew.trip.arrival) }}</span>
            <span>{{ t('Состояние') }}</span>
            <span><DictBadge :id="crew.trip.tripStatusType" dict="TripState" /></span>
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
            <button
              type="button"
              class="zs-btn-primary"
              :disabled="!canMark || !ready || running"
              :title="canMark ? '' : t('Начальник поезда этого рейса')"
              @click="mark(true)"
            >
              {{ t('Отметить отправление') }}
            </button>
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

import type { CouplingAct, CrewMember, StaffCandidate, TripCrew } from '@/api/trips'
import {
  fetchStaffCandidates,
  fetchTripCrew,
  setConductorWagons,
  setTripConductors,
  setTripDuty,
  setTripWagons,
} from '@/api/trips'
import { t } from '@/boot/i18n'
import { formatHm } from '@/desktop/routes/duration'
import { provideBusy } from '@/shared/busy'
import CbList from '@/shared/components/choice/CbList.vue'
import { openDialog } from '@/shared/components/dialog/openDialog'
import DictBadge from '@/shared/components/DictBadge.vue'
import ErrorBox from '@/shared/components/ErrorBox.vue'
import LoadSpinner from '@/shared/components/LoadSpinner.vue'
import {
  CouplingType,
  CrewDuty,
  LocationType,
  RoleType,
  TransferDirection,
  TripStatus,
} from '@/shared/constants/systemDicts'
import { dictManager } from '@/shared/dict/DictManager'
import type { ShownError } from '@/shared/errors'
import { shownError } from '@/shared/errors'
import { formatDateTime } from '@/shared/format'
import { useLoad } from '@/shared/loadable'
import type { WagonNorm } from '@/shared/trips/crewNorm'
import { crewNorm, normDone, slotOptions } from '@/shared/trips/crewNorm'
import { currentUser, hasRole, loadCurrentUser } from '@/shared/user'
import { placeText } from '@/shared/wagonPlace'
import DesktopFrame from '../frame/DesktopFrame.vue'
import SidePanel from '../frame/SidePanel.vue'
import SidePanelFields from '../frame/SidePanelFields.vue'
import CrewPickDialog from './CrewPickDialog.vue'
import TripMarkDialog from './TripMarkDialog.vue'
import TripWagonsDialog from './TripWagonsDialog.vue'

/**
 * Формирование рейса (Ф-РЕЙ-02, Ф-РЕЙ-06): слева состав и бригада, справа закреплённая панель рейса с готовностью и
 * отметкой отправления. Состав, начальника поезда, ПЭМ и проводников рейса в статусе «План» меняют планировщик и
 * администратор; проводников по вагонам распределяет и начальник поезда этого рейса, он и администратор отмечают
 * отправление и прибытие. Остальные видят экран без правки. Проверки — на сервере; здесь только доступность кнопок.
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
const canEdit = computed(() => planned.value && (isAdmin.value || hasRole(currentUser.value, RoleType.PLANNER)))
const isChief = computed(
  () => !!crew.value?.crew.some((m) => m.crewDutyType === CrewDuty.LNP && m.usr === currentUser.value?.id),
)
const canMark = computed(() => isChief.value || isAdmin.value)
const canCouple = computed(() => canMark.value || hasRole(currentUser.value, RoleType.MASTER))
const canAssign = computed(
  () => canEdit.value || (inProgress.value && isAdmin.value) || ((planned.value || inProgress.value) && isChief.value),
)

const back = () => void router.push({ name: 'trips' })
const label = (m: CrewMember) => (m.senior ? `${m.name} • ${t('ст.')}` : m.name)

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

const duties = computed(() => {
  const line = (duty: number, title: string, list: StaffCandidate[]) => {
    const member = crew.value?.crew.find((m) => m.crewDutyType === duty) ?? null
    const choices = list.map((c) => ({ id: c.usr, text: c.name }))
    // назначенный виден и если сейчас недоступен для выбора
    if (member && !choices.some((c) => c.id === member.usr)) choices.unshift({ id: member.usr, text: member.name })
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

const conductors = () => crew.value!.crew.filter((m) => m.crewDutyType === CrewDuty.CONDUCTOR).map((m) => m.usr)

function removeConductor(m: CrewMember) {
  void apply(() =>
    setTripConductors(
      tripId.value,
      conductors().filter((u) => u !== m.usr),
    ),
  )
}

async function pickConductors() {
  const trip = crew.value!.trip
  const saved = await openDialog<boolean>(CrewPickDialog, {
    tripId: trip.id,
    tripName: `${trip.trainNumber} ${trip.name}`,
    station: dictManager.getValue('Station', trip.departureStation),
  })
  if (saved) await load()
}

const slotChoices = (w: WagonNorm, slot: number) =>
  slotOptions(crew.value!, w, slot, label).map((o) => ({ id: o.user, text: o.label }))

function onSlot(w: WagonNorm, slot: number, user: number | null) {
  const prev = w.conductors[slot]
  if (prev?.usr === user) return
  const next = crew.value!.crew.find((m) => m.usr === user)
  void apply(async () => {
    let result = crew.value!
    // прежний проводник места уходит с этого вагона
    if (prev) {
      result = await setConductorWagons(
        tripId.value,
        prev.usr,
        prev.wagons.filter((x) => x !== w.number),
      )
    }
    // новый — на этот вагон; если у него уже есть соседний вагон, вагонов становится два
    if (next) result = await setConductorWagons(tripId.value, next.usr, [...next.wagons, w.number])
    return result
  })
}

const numbers = () => crew.value!.wagons.map((w) => w.number)

function moveWagon(i: number, step: number) {
  const list = numbers()
  const [wagon] = list.splice(i, 1)
  list.splice(i + step, 0, wagon!)
  void apply(() => setTripWagons(tripId.value, list))
}

function removeWagon(i: number) {
  const list = numbers()
  list.splice(i, 1)
  void apply(() => setTripWagons(tripId.value, list))
}

async function addWagons(pos: number) {
  const added = await openDialog<string[]>(TripWagonsDialog, {
    tripId: tripId.value,
    station: dictManager.getValue('Station', crew.value!.trip.departureStation),
  })
  if (!added) return
  const list = numbers()
  list.splice(pos, 0, ...added)
  void apply(() => setTripWagons(tripId.value, list))
}

// --- показ ---

const couple = (kind: string) => void router.push({ name: 'trip-coupling', params: { id: props.id, kind } })

const actTitle = (a: CouplingAct) =>
  t(a.couplingType === CouplingType.DETACH ? 'Отцепка на {station}' : 'Прицепка на {station}', {
    station: dictManager.getValue('Station', a.station),
  })

// строки таблицы — вагоны, которые едут в составе
const liveWagon = (i: number) => crew.value!.wagons.filter((w) => !w.detached)[i]!

const atStation = (i: number) => {
  const w = liveWagon(i)
  return w.locationType === LocationType.STATION && w.place === crew.value!.trip.departureStation
}
const isAway = (i: number) => planned.value && !atStation(i)

// вагон в депо или парке отстоя: передачу на станцию оформляют мастер депо, приёмщик и администратор
function canReturn(i: number): boolean {
  const u = currentUser.value
  const w = liveWagon(i)
  return (
    isAway(i) &&
    (w.locationType === LocationType.DEPOT || w.locationType === LocationType.YARD) &&
    (hasRole(u, RoleType.ADMIN) || hasRole(u, RoleType.MASTER) || hasRole(u, RoleType.RECEIVER))
  )
}

function returnWagon(i: number) {
  const w = liveWagon(i)
  void router.push({
    name: 'transfer',
    query: {
      dir: TransferDirection.FROM_DEPOT,
      station: crew.value!.trip.departureStation,
      ...(w.locationType === LocationType.DEPOT ? { depot: w.place } : { yard: w.place }),
      wagons: w.number,
    },
  })
}

function where(i: number): string {
  const w = liveWagon(i)
  if (w.locationType === LocationType.TRIP && w.place === crew.value!.trip.id) return t('В составе')
  return placeText(w.locationType, w.place, `${w.trainNumber ?? ''} ${w.tripName ?? ''}`.trim())
}

function conductorsText(w: WagonNorm): string {
  if (!w.passenger) return t('не требуется')
  if (w.conductors.length === 0) return t('не назначены')
  return w.conductors.map(label).join(', ')
}

const wagonsReady = computed(() => {
  const all = crew.value?.wagons ?? []
  return all.length > 0 && all.every((_, i) => atStation(i))
})

const readiness = computed(() => {
  const n = norm.value!
  const all = crew.value!.wagons
  const station = dictManager.getValue('Station', crew.value!.trip.departureStation)
  const chief = crew.value!.crew.find((m) => m.crewDutyType === CrewDuty.LNP)
  const pem = crew.value!.crew.find((m) => m.crewDutyType === CrewDuty.PEM)
  return [
    {
      ok: wagonsReady.value,
      title: t('Вагоны на станции отправления'),
      detail: all.length
        ? t('{a} из {b} на {station}', { a: all.filter((_, i) => atStation(i)).length, b: all.length, station })
        : t('состав не сформирован'),
    },
    {
      ok: n.chiefAndPem,
      title: t('Начальник поезда и ПЭМ'),
      detail: `${t('ЛНП')} ${chief?.name ?? '—'} • ${t('ПЭМ')} ${pem?.name ?? '—'}`,
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

async function mark(depart: boolean) {
  const result = await openDialog<TripCrew>(TripMarkDialog, { tripId: tripId.value, depart })
  if (!result) return
  crew.value = result
  Notify.create({
    message: depart ? t('Отправление отмечено') : t('Прибытие отмечено'),
    type: 'positive',
    position: 'bottom',
  })
  void loadCurrentUser(true)
}
</script>

<style scoped lang="scss">
.trip-form {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  padding: 0 0 1rem;
  overflow-y: auto;
}

.trip-form__error {
  margin-bottom: 0.75rem;
}

.trip-form__caption {
  color: $zs-text-muted;
  font-size: 0.75rem;
  font-weight: 600;
  letter-spacing: 0.06em;
  text-transform: uppercase;
}

.trip-form__staff {
  display: flex;
  flex-wrap: wrap;
  gap: 0.75rem 1.75rem;
}

.trip-form__duty {
  display: flex;
  align-items: center;
  gap: 0.625rem;
  color: $zs-text-muted;
  font-size: 0.8125rem;
}

.trip-form__staff-name {
  color: $zs-text;
  font-weight: 600;
}

.trip-form__pool {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.625rem 0.75rem;
  border: 0.0625rem solid $zs-border;
  border-radius: 0.25rem;
  background: $zs-bg-header;
  font-size: 0.8125rem;
}

.trip-form__pool-title {
  flex: none;
  font-weight: 600;
}

.trip-form__chips {
  display: flex;
  flex: 1;
  flex-wrap: wrap;
  gap: 0.375rem;
  min-width: 0;
}

.trip-form__chip {
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
  padding: 0.125rem 0.5rem;
  border: 0.0625rem solid $zs-border;
  border-radius: 62.5rem;
  background: #fff;
}

.trip-form__chip-del {
  padding: 0;
  border: 0;
  background: none;
  color: $zs-text-muted;
  cursor: pointer;
}

.trip-form__btn {
  flex: none;
  height: var(--zs-control-height);
  padding: 0 0.75rem;
  border: 0.0625rem solid $zs-border-input;
  border-radius: 0.25rem;
  background: #fff;
  color: $primary;
  font: inherit;
  font-size: 0.8125rem;
  font-weight: 600;
  white-space: nowrap;
  cursor: pointer;

  &:hover {
    background: $zs-bg-hover;
  }
}

.trip-form__table {
  border: 0.0625rem solid $zs-border;
  border-radius: 0.25rem;
  background: #fff;
}

.trip-form__row {
  display: grid;
  grid-template-columns: 2.5rem 8rem minmax(9rem, 1fr) minmax(18rem, 2fr) 12rem;
  align-items: center;
  gap: 0.75rem;
  padding: 0.5rem 0.75rem;
  border-top: 0.0625rem solid $zs-border-row;
  font-size: 0.8125rem;

  &--head {
    border-top: 0;
    background: $zs-bg-header;
    color: $zs-text-muted;
    font-size: 0.75rem;
    font-weight: 600;
  }
}

.trip-form__number {
  font-weight: 700;
}

.trip-form__block {
  display: block;
}

.trip-form__muted {
  color: $zs-text-muted;
  font-size: 0.8125rem;
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
  gap: 0.375rem;
}

.trip-form__slot {
  min-width: 0;

  &--bad {
    outline: 0.0625rem solid $zs-error-fg;
  }
}

.trip-form__link {
  display: block;
  padding: 0;
  border: 0;
  background: none;
  color: $primary;
  font: inherit;
  font-size: 0.75rem;
  font-weight: 600;
  cursor: pointer;
}

.trip-form__moves {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 0.25rem;
}

.trip-form__move {
  width: 1.8rem;
  height: 1.8rem;
  padding: 0;
  border: 0.0625rem solid $zs-border-input;
  border-radius: 0.25rem;
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
  gap: 0.75rem;
  padding: 1rem 0.75rem;
  color: $zs-text-muted;
  font-size: 0.8125rem;
}

.trip-form__act {
  display: grid;
  grid-template-columns: 6rem minmax(10rem, 1fr) minmax(10rem, 1fr) 8rem;
  gap: 0.75rem;
  padding: 0.5rem 0.75rem;
  border-top: 0.0625rem solid $zs-border-row;
  font-size: 0.8125rem;

  &:first-child {
    border-top: 0;
  }
}

.trip-form__ready {
  display: flex;
  gap: 0.5rem;
  font-size: 0.8125rem;
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
  font-size: 0.8125rem;
  font-weight: 600;
}
</style>
