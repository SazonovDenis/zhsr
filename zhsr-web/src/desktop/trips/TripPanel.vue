<template>
  <SidePanel
    :empty="trip ? '' : 'Выберите рейс в списке'"
    :title="trip?.name ?? ''"
    :subtitle="trip ? t('№ {train}', { train: trip.trainNumber }) : ''"
    @close="emit('close')"
  >
    <template v-if="trip">
      <TripFields :trip="trip" />
      <!-- «Редактировать» — кнопкой, под ней переходы в другие окна мягкими ссылками -->
      <div v-if="editAction" class="q-mt-md">
        <q-btn
          unelevated
          outline
          no-caps
          color="primary"
          class="zs-soft-border"
          :disable="!!editAction.reason"
          :title="editAction.reason || undefined"
          @click="editAction.run()"
        >
          {{ t(editAction.label) }}
        </q-btn>
      </div>
      <div class="trip-panel__links q-mt-md">
        <SoftLink
          v-for="l in links"
          :key="l.label"
          :text="l.label"
          :disabled="!!l.reason"
          :title="l.reason || undefined"
          @click="l.run()"
        />
      </div>

      <ErrorBox v-if="error" :error="error" :retry="load" class="q-mt-md" />

      <!-- поездная бригада: ЛНП, ПЭМ, проводники; «Изменить» — диалог «Поездная бригада» -->
      <div class="trip-panel__section q-mt-md">
        <div class="trip-panel__head">
          <span class="trip-panel__caption">{{ t('Поездная бригада') }}</span>
          <SoftLink
            text="Изменить"
            :disabled="!!crewReason || !crew"
            :title="crewReason || undefined"
            @click="editCrew"
          />
        </div>
        <template v-if="crew">
          <div class="trip-panel__list">
            <template v-for="m in crewMembers" :key="m.usr.id">
              <span><UserName :user="m.usr" /></span>
              <span class="trip-panel__muted">{{ dutyText(m) }}</span>
            </template>
          </div>
          <div v-if="crewMembers.length === 0" class="trip-panel__muted">{{ t('Бригада рейса не назначена') }}</div>
        </template>
      </div>

      <!-- ответственные за вагоны: на рейс или после рейса; «Изменить» — диалог ответственных -->
      <div class="trip-panel__section q-mt-md">
        <div class="trip-panel__head">
          <span class="trip-panel__caption">{{ t('Ответственные') }}</span>
          <SoftLink
            text="Изменить"
            :disabled="!!respReason"
            :title="respReason || undefined"
            @click="editResponsibles"
          />
        </div>
        <!-- переключатель «На рейс» / «После рейса» — строкой ниже подписи -->
        <div>
          <CbSegment v-model="layer" single required :values="layers" />
        </div>
        <template v-if="resp">
          <div class="trip-panel__muted">{{ respCaption }}</div>
          <div class="trip-panel__list">
            <template v-for="r in respLines" :key="r.key">
              <span :class="{ 'trip-panel__muted': !r.usr }">
                <UserName v-if="r.usr" :user="r.usr" /><template v-else>{{ r.empty }}</template>
              </span>
              <span>{{ t('вагон {n}', { n: r.number }) }}</span>
            </template>
          </div>
          <div v-if="respLines.length === 0" class="trip-panel__muted">{{ t('В поезде рейса нет вагонов') }}</div>
        </template>
      </div>
    </template>
  </SidePanel>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, shallowRef, watch } from 'vue'

import type { CrewMember, TripCrew, TripResponsibles, TripRow } from '@/api/trips'
import { fetchTripCrew, fetchTripResponsibles } from '@/api/trips'
import type { UserRef } from '@/api/requests'
import { t } from '@/boot/i18n'
import type { ChoiceItem } from '@/shared/components/choice/choice'
import CbSegment from '@/shared/components/choice/CbSegment.vue'
import { openDialog } from '@/shared/components/dialog/openDialog'
import ErrorBox from '@/shared/components/ErrorBox.vue'
import SoftLink from '@/shared/components/form/SoftLink.vue'
import UserName from '@/shared/components/UserName.vue'
import { CrewDuty, RoleType, TripStatus } from '@/shared/constants/systemDicts'
import { TripResponsibleLayer } from '@/shared/constants/TripResponsibleLayer'
import { dictManager } from '@/shared/dict/DictManager'
import type { ShownError } from '@/shared/errors'
import { shownError } from '@/shared/errors'
import { formatDateTime } from '@/shared/format'
import { currentUser, hasRole, loadCurrentUser } from '@/shared/user'
import SidePanel from '../frame/SidePanel.vue'
import CrewPickDialog from './CrewPickDialog.vue'
import { NAV_ACTIONS, useTripActions } from './tripActions'
import TripFields from './TripFields.vue'
import TripResponsiblesDialog from './TripResponsiblesDialog.vue'

/**
 * Правая панель списка рейсов: заголовок — маршрут, атрибуты рейса (TripFields), кнопка «Редактировать», переходы
 * мягкими ссылками («Формирование поезда» и другие). Ниже — «Поездная бригада» — сотрудники с должностью; «Изменить» —
 * диалог «Поездная бригада» (CrewPickDialog). Затем «Ответственные»: подпись и «Изменить», строкой ниже —
 * переключатель «На рейс» / «После рейса», под ним подпись «Ответственные с …» и строки «ФИО — вагон N», номера
 * вагонов — колонкой; «Изменить» — диалог ответственных (TripResponsiblesDialog). Готовность к отправлению — колонкой таблицы. Событие changed — рейс изменился.
 */
const props = defineProps<{ trip: TripRow | null }>()

const emit = defineEmits<{ close: []; changed: [] }>()

const { actionsOf } = useTripActions(() => emit('changed'))

const EDIT = 'Редактировать'
const actions = computed(() => (props.trip ? actionsOf(props.trip) : []))
/** Переходы в другие окна; reason — почему недоступно сейчас. */
const links = computed(() => actions.value.filter((a) => NAV_ACTIONS.includes(a.label)))
const editAction = computed(() => actions.value.find((a) => a.label === EDIT))

const crew = shallowRef<TripCrew | null>(null)
const resp = shallowRef<TripResponsibles | null>(null)
const error = ref<ShownError | null>(null)

type Layer = 'trip' | 'after'
const layer = ref<Layer>('trip')
const layers: ChoiceItem[] = [
  { id: 'trip', text: 'На рейс' },
  { id: 'after', text: 'После рейса' },
]

async function loadResp() {
  const trip = props.trip
  if (!trip) return
  resp.value = null
  const d = await fetchTripResponsibles(
    trip.id,
    layer.value === 'after' ? TripResponsibleLayer.AFTER : TripResponsibleLayer.TRIP,
  )
  if (props.trip?.id === trip.id) resp.value = d
}

async function loadCrew() {
  const trip = props.trip
  if (!trip) return
  const c = await fetchTripCrew(trip.id)
  if (props.trip?.id === trip.id) crew.value = c
}

async function load() {
  error.value = null
  try {
    await Promise.all([loadCrew(), loadResp(), dictManager.prepareDict('Station')])
  } catch (e) {
    error.value = shownError(e)
  }
}

watch(
  () => props.trip?.id,
  () => {
    crew.value = null
    resp.value = null
    void load()
  },
  { immediate: true },
)
watch(layer, async () => {
  error.value = null
  try {
    await loadResp()
  } catch (e) {
    error.value = shownError(e)
  }
})

/** Подпись: «Ответственные с …» — с начала самых поздних показанных ответственных; ответственных нет — без даты. */
const respCaption = computed(() => {
  const starts = (resp.value?.wagons ?? []).map((w) => w.dbeg).filter((d): d is string => !!d)
  if (starts.length === 0) return t(layer.value === 'after' ? 'Плана после рейса нет' : 'Ответственные не назначены')
  const last = starts.reduce((a, b) => (new Date(b) > new Date(a) ? b : a))
  return t('Ответственные с {at}', { at: formatDateTime(last) })
})

/** Строки ответственных: каждый сотрудник вагона — своей строкой; вагон без ответственных — одной строкой. */
const respLines = computed(() => {
  const res: { key: string; usr: UserRef | null; empty: string; number: string }[] = []
  for (const w of resp.value?.wagons ?? []) {
    if (w.usrs.length === 0) {
      const empty = t(layer.value === 'after' ? 'без плана' : 'не назначен')
      res.push({ key: `${w.wagon.id}`, usr: null, empty, number: w.wagon.number })
    }
    for (const u of w.usrs) res.push({ key: `${w.wagon.id}-${u.id}`, usr: u, empty: '', number: w.wagon.number })
  }
  return res
})

// бригада: ЛНП, ПЭМ, затем проводники
const DUTY_ORDER: number[] = [CrewDuty.LNP, CrewDuty.PEM, CrewDuty.CONDUCTOR]
const crewMembers = computed(() =>
  [...(crew.value?.crew ?? [])].sort((a, b) => DUTY_ORDER.indexOf(a.crewDutyType) - DUTY_ORDER.indexOf(b.crewDutyType)),
)
const dutyText = (m: CrewMember) =>
  dictManager.getValue('CrewDuty', m.crewDutyType) + (m.senior ? ` • ${t('ст.')}` : '')

/** Почему действие недоступно в статусе рейса; пусто — доступно. */
function statusReason(...allowed: number[]): string {
  const s = props.trip?.tripStatusType
  if (s !== undefined && allowed.includes(s)) return ''
  return t('Недоступно в статусе «{status}»', { status: dictManager.getValue('TripState', s) })
}

/** Почему ответственных не изменить: нет прав или рейс отменён; пусто — можно. */
const respReason = computed(() => {
  const u = currentUser.value
  const chief = u?.trip?.id === props.trip?.id && u?.trip?.crewDutyType === CrewDuty.LNP
  const rights = chief || [RoleType.ADMIN, RoleType.WAGON_PLANNER, RoleType.CREW_PLANNER].some((r) => hasRole(u, r))
  if (!rights) return t('Ответственных за вагоны меняют начальник поезда рейса и планировщики')
  return statusReason(TripStatus.PLANNED, TripStatus.IN_PROGRESS, TripStatus.DONE)
})

/** Почему поездную бригаду не поменять: нет прав или рейс не в статусе «План»; пусто — можно. */
const crewReason = computed(() => {
  const u = currentUser.value
  if (![RoleType.ADMIN, RoleType.WAGON_PLANNER, RoleType.CREW_PLANNER].some((r) => hasRole(u, r)))
    return t('Поездную бригаду меняют планировщик вагонов и планировщик проводников')
  return statusReason(TripStatus.PLANNED)
})

async function editResponsibles() {
  const trip = props.trip
  if (!trip) return
  const saved = await openDialog<boolean>(TripResponsiblesDialog, { tripId: trip.id, after: layer.value === 'after' })
  // ответственные меняют норму проводников: список перечитывается ради колонки «Готовность»
  if (saved) {
    void load()
    emit('changed')
  }
}

async function editCrew() {
  const c = crew.value
  if (!c) return
  const saved = await openDialog<boolean>(CrewPickDialog, {
    tripId: c.trip.id,
    tripName: `${c.trip.trainNumber} • ${c.trip.name}`,
    station: c.trip.departureStation ? dictManager.getValue('Station', c.trip.departureStation) : '',
    crew: c.crew,
  })
  // бригада меняет готовность (ПЭМ, ЛНП, проводники): список перечитывается
  if (saved) {
    void load()
    emit('changed')
  }
}

onMounted(() => void loadCurrentUser())
</script>

<style scoped lang="scss">
.trip-panel__links {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem 1.2rem;
}

.trip-panel__section {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  font-size: 0.8rem;
}

.trip-panel__head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.5rem 1rem;
}

.trip-panel__caption {
  color: $zs-text-muted;
  font-size: 0.9rem;
  font-weight: 600;
}

// ФИО — каждое своей строкой, вторая колонка (вагон, должность) — вровень
.trip-panel__list {
  display: grid;
  grid-template-columns: minmax(0, max-content) max-content;
  gap: 0.2rem 1.2rem;
}

.trip-panel__muted {
  color: $zs-text-muted;
}
</style>
