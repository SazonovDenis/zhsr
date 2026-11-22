<template>
  <DesktopFrame v-model:floating="floating" :back="back" :right-open="true">
    <template #header>
      <h1 class="desktop-frame__title">{{ t('Формирование рейса') }}</h1>
      <!-- действия рейса в пути и после прибытия: видны все; нет прав или не тот статус — неактивна, причина рядом -->
      <div v-if="crew && !planned && headerButtons.length > 0" class="desktop-frame__actions">
        <q-btn
          v-for="b in headerButtons"
          :key="b.label"
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
      </div>
    </template>

    <ErrorBox v-if="error" :error="error" :retry="crew ? null : load" class="q-mb-md" />
    <LoadSpinner v-if="busy.active.value && !crew" />

    <div v-if="crew && norm" class="trip-form q-pb-md">
      <!-- поезд рейса: состав на время рейса — до отправления ожидаемый, в пути и после — по факту -->
      <section class="trip-form__section">
        <div class="trip-form__head">
          <span class="trip-form__caption">{{ t('Поезд') }}</span>
          <template v-if="crew.trip.train">
            <SoftLink text="{name} — состав и журнал" :params="{ name: crew.trip.train.name }" @click="openTrain" />
            <span class="trip-form__muted">{{ compositionNote }}</span>
          </template>
          <template v-else>
            <span class="trip-form__bad">{{ t('не назначен') }}</span>
            <q-btn
              unelevated
              outline
              no-caps
              color="primary"
              class="zs-soft-border"
              :disable="!!tripEditReason"
              :title="tripEditReason || undefined"
              :label="t('Выбрать поезд')"
              @click="chooseTrain"
            />
          </template>
          <q-btn
            v-if="crew.trip.train"
            unelevated
            outline
            no-caps
            color="primary"
            class="zs-soft-border q-ml-auto"
            :disable="!!attachReason || running || actions.running.value"
            :title="attachReason || undefined"
            :label="t('Прицепить вагоны')"
            @click="actions.attach"
          />
        </div>

        <!-- в пути перестановка проводников — смена ответственных за вагоны: записывается этим временем -->
        <div v-if="inProgress && canAssign" class="row items-center q-gutter-sm">
          <span>{{ t('Время перестановки проводников') }}</span>
          <DatePicker v-model="moveTime" with-time date-text="Время изменения" />
        </div>

        <div v-if="crew.trip.train" class="trip-form__table">
          <div class="trip-form__row trip-form__row--head q-py-sm q-px-md">
            <span>{{ t('№') }}</span>
            <span>{{ t('Вагон') }}</span>
            <span>{{ t('Где сейчас') }}</span>
            <span>{{ t('Прицепка и отцепка в рейсе') }}</span>
            <span>{{ t('Проводники') }}</span>
            <span />
          </div>
          <div
            v-for="w in crew.wagons"
            :key="w.wagon"
            class="trip-form__row q-py-sm q-px-md"
            :class="{ 'trip-form__row--off': !w.riding }"
          >
            <span>{{ w.riding ? w.seq : '' }}</span>
            <span>
              <span class="zs-mono trip-form__number">{{ w.number }}</span>
              <span class="trip-form__muted trip-form__block">{{
                dictManager.getValue('WagonType', w.wagonType)
              }}</span>
            </span>
            <span>
              {{ where(w) }}
              <template v-if="isAway(w)">
                <span class="trip-form__warn trip-form__block">{{ t('не на станции отправления') }}</span>
                <button
                  type="button"
                  class="trip-form__link q-pa-none"
                  :disabled="!!returnReason(w)"
                  :title="returnReason(w) || undefined"
                  @click="returnWagon(w)"
                >
                  {{ t('Оформить передачу') }} →
                </button>
              </template>
            </span>
            <span class="trip-form__changes">
              <span v-if="!w.riding && w.changes.length === 0" class="trip-form__muted">{{ notRidingText() }}</span>
              <ChangeLine
                v-for="c in w.changes"
                :key="c.id"
                :change="c"
                :facts="crew.changes"
                :disable="actions.running.value"
                @mark="actions.mark"
                @cancel="actions.cancel"
                @undo="actions.undo"
              />
            </span>
            <span>
              <!-- проводники — у вагонов, которые едут в поезде рейса -->
              <template v-for="n in [normOf(w.number)]" :key="n ? n.number : w.number">
                <template v-if="!n" />
                <template v-else-if="canAssign && n.passenger">
                  <span class="trip-form__slots">
                    <CbList
                      v-for="slot in [0, 1]"
                      :key="slot"
                      single
                      no-search
                      placeholder="Не назначен"
                      class="trip-form__slot"
                      :class="{ 'trip-form__slot--bad': n.notAdjacent && !!n.conductors[slot] }"
                      :model-value="n.conductors[slot]?.usr.id ?? null"
                      :values="slotChoices(n, slot)"
                      @update:model-value="onSlot(n, slot, $event as number | null)"
                    >
                      <!-- «Список ФИО»: ФИО слева, справа должность, под ней — где проводник сейчас -->
                      <template #option="{ item }">
                        <UserOption
                          :name="(item.data?.name as string | undefined) ?? ''"
                          :me="(item.data?.me as boolean | undefined) ?? false"
                          :duty="(item.data?.duty as string | undefined) ?? ''"
                          :place="(item.data?.place as string | undefined) ?? ''"
                        />
                      </template>
                    </CbList>
                  </span>
                  <span v-if="n.notAdjacent" class="trip-form__bad trip-form__block">
                    {{ t('назначен на несоседние вагоны') }}
                  </span>
                </template>
                <!-- проводники вагона через запятую; нет — «не назначены», вагону не нужны — «не требуется» -->
                <span v-else :class="{ 'trip-form__muted': !n.passenger }">
                  <template v-if="n.passenger && n.conductors.length > 0">
                    <template v-for="(m, ci) in n.conductors" :key="m.usr.id">
                      <template v-if="ci > 0">, </template>
                      <UserName :user="m.usr" /><template v-if="m.senior"> • {{ t('ст.') }}</template>
                    </template>
                  </template>
                  <template v-else>{{ n.passenger ? t('не назначены') : t('не требуется') }}</template>
                </span>
              </template>
            </span>
            <button
              type="button"
              class="trip-form__menu q-pa-none"
              :title="t('Действия')"
              :aria-label="t('Действия')"
              @click="openMenu($event, w)"
            >
              <q-icon name="more_vert" size="1.2rem" />
            </button>
          </div>
          <div v-if="crew.wagons.length === 0" class="trip-form__muted q-pa-md">{{ t('В поезде нет вагонов') }}</div>
        </div>
      </section>

      <!-- бригада: начальник поезда и ПЭМ -->
      <section class="trip-form__section">
        <div class="trip-form__head">
          <span class="trip-form__caption">{{ t('Бригада') }}</span>
        </div>
        <FormSection columns="repeat(2, minmax(0, 18rem))">
          <FormField v-for="d in duties" :key="d.duty" :label="d.title">
            <CbList
              v-if="canEdit"
              single
              placeholder="Не назначен"
              :model-value="d.member?.usr.id ?? null"
              :values="d.choices"
              :source="staffLoad"
              @update:model-value="setDuty(d.duty, $event as number | null)"
            >
              <!-- «Список ФИО»; занятый в пересекающемся рейсе виден с пометкой справа; назначить его не даст сервер -->
              <template #option="{ item }">
                <UserOption
                  :name="item.text ?? ''"
                  :me="isMe({ id: Number(item.id) })"
                  :duty="t(d.title)"
                  :place="(item.data?.busy as string | undefined) ?? ''"
                />
              </template>
            </CbList>
            <span v-else class="trip-form__name" :class="{ 'trip-form__bad': !d.member }">
              <UserName v-if="d.member" :user="d.member.usr" /><template v-else>{{ t('не назначен') }}</template>
            </span>
          </FormField>
        </FormSection>
      </section>
    </div>
    <RowMenu ref="menuEl" />

    <template #right>
      <SidePanel
        :empty="crew ? '' : ' '"
        :title="crew?.trip.name ?? ''"
        :subtitle="crew ? t('№ {train}', { train: crew.trip.trainNumber }) : ''"
        :closable="false"
      >
        <template v-if="crew && norm">
          <!-- атрибуты — как в панели списка рейсов; «Редактировать» — над чертой, ниже — готовность и отправление -->
          <TripFields :trip="crew.trip" />
          <div class="q-mt-md">
            <SoftLink text="Ответственные за вагоны" @click="editResponsibles" />
          </div>
          <div class="q-mt-md">
            <q-btn
              unelevated
              outline
              no-caps
              color="primary"
              class="zs-soft-border"
              :disable="!!tripEditReason || running"
              :title="tripEditReason || undefined"
              :label="t('Редактировать')"
              @click="editTrip"
            />
          </div>
          <div class="trip-form__divider q-mt-md" />

          <TripReadiness
            v-if="planned"
            :crew="crew"
            :mark-reason="canMark ? '' : t(MARK_REASON)"
            :running="running"
            @mark="mark(true)"
          />
        </template>
      </SidePanel>
    </template>
  </DesktopFrame>
</template>

<script setup lang="ts">
import { date, Notify } from 'quasar'
import { computed, ref, shallowRef, useTemplateRef, watch } from 'vue'
import { useRouter } from 'vue-router'

import { fetchTrain } from '@/api/trains'
import type { CrewMember, CrewWagon, MemberWagons, StaffCandidate, TripCrew, TripRow } from '@/api/trips'
import {
  cancelArrival,
  cancelDeparture,
  fetchStaffCandidates,
  fetchTripCrew,
  setCrewWagons,
  setTripDuty,
} from '@/api/trips'
import { t } from '@/boot/i18n'
import { provideBusy } from '@/shared/busy'
import CbList from '@/shared/components/choice/CbList.vue'
import { openDialog } from '@/shared/components/dialog/openDialog'
import ErrorBox from '@/shared/components/ErrorBox.vue'
import FormField from '@/shared/components/form/FormField.vue'
import FormSection from '@/shared/components/form/FormSection.vue'
import SoftLink from '@/shared/components/form/SoftLink.vue'
import LoadSpinner from '@/shared/components/LoadSpinner.vue'
import DatePicker from '@/shared/components/period/DatePicker.vue'
import RowMenu from '@/shared/components/table/RowMenu.vue'
import UserName from '@/shared/components/UserName.vue'
import UserOption from '@/shared/components/UserOption.vue'
import { CrewDuty, LocationType, RoleType, TripStatus } from '@/shared/constants/systemDicts'
import { dictManager } from '@/shared/dict/DictManager'
import type { ShownError } from '@/shared/errors'
import { shownError } from '@/shared/errors'
import { formatDateTime, isoDateTime } from '@/shared/format'
import { useLoad } from '@/shared/loadable'
import { isAttach, isFact, isWaiting } from '@/shared/trains/trainJournal'
import type { WagonNorm } from '@/shared/trips/crewNorm'
import { conductorDuty, crewNorm, slotOptions } from '@/shared/trips/crewNorm'
import { tripRefText } from '@/shared/trips/tripRefText'
import { markEditable, useNow } from '@/shared/trips/tripTimes'
import { currentUser, hasRole, isMe, loadCurrentUser, userName } from '@/shared/user'
import { placeText } from '@/shared/wagonPlace'
import DesktopFrame from '../frame/DesktopFrame.vue'
import SidePanel from '../frame/SidePanel.vue'
import ChangeLine from '../trains/ChangeLine.vue'
import { useTrainActions } from '../trains/useTrainActions'
import TripFields from './TripFields.vue'
import TripMarkDialog from './TripMarkDialog.vue'
import TripNewDialog from './TripNewDialog.vue'
import TripReadiness from './TripReadiness.vue'
import TripResponsiblesDialog from './TripResponsiblesDialog.vue'
import { useTripDialogReturn } from './useTripDialogReturn'

/**
 * Формирование рейса (Ф-РЕЙ-02, Ф-РЕЙ-06): слева поезд рейса и бригада, справа закреплённая панель рейса с готовностью
 * и отметкой отправления. Вагоны поезда на время рейса: до отправления — ожидаемый состав на отправление (факт и
 * ожидающие планы до него), в пути и после — по факту; у вагона — его прицепки и отцепки в этом рейсе с действиями
 * «Отметить», «Отменить план», «Удалить факт». Вагоны, которые прицепляют в пути или отцепили, — внизу, бледно. В норму
 * проводников идут вагоны, которые едут на отправлении. Состав и журнал поезда — экран поезда (ссылка у поезда).
 * Прицепляют и отцепляют планировщик вагонов и администратор (меню вагона и кнопка «Прицепить вагоны»); начальника
 * поезда и ПЭМ назначает планировщик вагонов; список проводников рейса — в панели списка рейсов; проводников по вагонам
 * распределяют планировщик проводников и начальник поезда этого рейса. Начальник поезда отмечает планы рейса,
 * отправление и прибытие. Проверки — на сервере; здесь только доступность кнопок.
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
const isPlanner = computed(() => isAdmin.value || hasRole(currentUser.value, RoleType.WAGON_PLANNER))
// начальник поезда и ПЭМ — планировщик вагонов
const canEdit = computed(() => planned.value && isPlanner.value)
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
const COUPLE_REASON = 'Прицепку и отцепку в рейсе оформляет ЛНП этого рейса или мастер депо'
const TRANSFER_REASON = 'Передачу оформляет мастер депо или приёмщик'

/** Почему кнопка недоступна: сначала нет прав (rights — права есть, reason — текст), затем status; пусто — доступна. */
const why = (rights: boolean, reason: string, status: string) => (rights ? status : t(reason))

/** Почему рейс не исправить и поезд не выбрать: нет прав или рейс не в статусе «План»; пусто — можно. */
const tripEditReason = computed(() => why(isPlanner.value, PLANNER_REASON, statusReason(TripStatus.PLANNED)))
/** Почему не прицепить вагоны: план и факт до отправления, планы в пути — планировщик вагонов. */
const attachReason = computed(() =>
  why(isPlanner.value, PLANNER_REASON, statusReason(TripStatus.PLANNED, TripStatus.IN_PROGRESS)),
)

/** Ожидающие планы рейса по времени: «Отметить прицепку» в шапке отмечает ближайший. */
const plannedChanges = computed(() => (crew.value?.changes ?? []).filter(isWaiting))

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
    run: () => void actions.mark(plannedChanges.value[0]!),
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
    (planned.value && (isAdmin.value || hasRole(currentUser.value, RoleType.CREW_PLANNER))) ||
    (inProgress.value && isAdmin.value) ||
    ((planned.value || inProgress.value) && isChief.value),
)

const back = () => void router.push({ name: 'trips' })
const label = (m: CrewMember) => (m.senior ? `${userName(m.usr)} • ${t('ст.')}` : userName(m.usr))

async function load() {
  error.value = null
  try {
    await busy.run(async () => {
      const [data] = await Promise.all([
        fetchTripCrew(tripId.value),
        dictManager.prepareDicts('Station', 'Depot', 'Yard', 'WagonType', 'TransportType', 'TripState'),
        loadCurrentUser(),
      ])
      crew.value = data
    })
  } catch (e) {
    error.value = shownError(e)
  }
}
void load()

/** Перечитать рейс без спиннера: после изменения состава. */
async function reloadCrew() {
  try {
    crew.value = await fetchTripCrew(tripId.value)
  } catch (e) {
    error.value = shownError(e)
  }
}

// --- состав: те же действия, что на экране поезда; поезд загружается к действию ---

const actions = useTrainActions({
  card: () => fetchTrain(crew.value!.trip.train!.id),
  at: () => crew.value!.trip.plannedDt,
  trip: () => tripId.value,
  inProgress: (c) => inProgress.value && c.trip?.id === tripId.value,
  changed: async () => {
    error.value = null
    await reloadCrew()
  },
  failed: (e) => (error.value = shownError(e)),
})

/** Пояснение к составу: какой состав показан. */
const compositionNote = computed(() => {
  const trip = crew.value!.trip
  if (planned.value)
    return t('ожидаемый состав на отправление {time}: факт и планы до него', { time: formatDateTime(trip.plannedDt) })
  if (inProgress.value) return t('состав по факту; в пути прицепляют и отцепляют кнопками в шапке')
  return t('состав по факту на прибытие')
})

function where(w: CrewWagon): string {
  if (w.locationType === LocationType.TRIP) return placeText(w.locationType, null, w.trip ? tripRefText(w.trip) : null)
  return placeText(w.locationType, w.place)
}

/** Вагон не едет в поезде рейса и прицепок в рейсе у него нет: отцеплен до или прицепится после. */
function notRidingText(): string {
  return planned.value ? t('прицепится в пути') : t('не в поезде')
}

const menuEl = useTemplateRef<InstanceType<typeof RowMenu>>('menuEl')

/** Меню вагона: «Отцепить вагон» (факт до отправления) и «Запланировать отцепку»; недоступное — с причиной. */
function openMenu(e: MouseEvent, w: CrewWagon) {
  const rights = why(isPlanner.value, PLANNER_REASON, '')
  // фактом до отправления отцепляют вагон, который в поезде по факту: не ожидает плановой прицепки
  const pendingAttach = w.changes.some((c) => isWaiting(c) && isAttach(c))
  const detachedHere = w.changes.some((c) => isFact(c) && !isAttach(c))
  const fact =
    rights ||
    statusReason(TripStatus.PLANNED) ||
    (!w.riding || pendingAttach ? t('Вагон ещё не в поезде по факту') : '')
  const plan =
    rights || statusReason(TripStatus.PLANNED, TripStatus.IN_PROGRESS) || (detachedHere ? t('Вагон уже отцеплен') : '')
  const items = [
    { label: 'Отцепить вагон', reason: fact, separator: false, run: () => void actions.detach(w, true) },
    { label: 'Запланировать отцепку', reason: plan, separator: false, run: () => void actions.detach(w, false, true) },
  ]
  void menuEl.value?.show(items, e.clientX, e.clientY, true)
}

function openTrain() {
  void router.push({ name: 'train-form', params: { id: crew.value!.trip.train!.id } })
}

// --- бригада ---

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

/** Правка бригады: ответ сервера — рейс после правки; ошибка — над экраном. */
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

const slotChoices = (w: WagonNorm, slot: number) =>
  slotOptions(crew.value!, w, slot, label).map((o) => ({
    id: o.user,
    text: o.label,
    data: {
      name: o.member.usr.name,
      me: isMe(o.member.usr),
      duty: conductorDuty(o.member, dictManager.getValue('CrewDuty', o.member.crewDutyType)),
      place: o.place,
    },
  }))

// время перестановки проводников в пути; значение DatePicker — время браузера без пояса, на сервер уходит с поясом
const moveTime = ref<string | null>(date.formatDate(new Date(), 'YYYY-MM-DDTHH:mm'))

/**
 * Проводник на место в вагоне — одной операцией: до отправления меняется план ответственных на рейс, в пути — факт
 * временем перестановки.
 */
function onSlot(w: WagonNorm, slot: number, user: number | null) {
  const prev = w.conductors[slot]
  if (prev?.usr.id === user) return
  const next = crew.value!.crew.find((m) => m.usr.id === user)
  const members: MemberWagons[] = []
  // прежний проводник места уходит с этого вагона
  if (prev) members.push({ usr: prev.usr.id, wagons: prev.wagons.filter((x) => x !== w.number) })
  // новый — на этот вагон; если у него уже есть соседний вагон, вагонов становится два
  if (next) members.push({ usr: next.usr.id, wagons: [...next.wagons, w.number] })
  const time = inProgress.value && moveTime.value ? isoDateTime(new Date(moveTime.value)) : null
  void apply(() => setCrewWagons(tripId.value, members, time))
}

/** Норма проводников вагона: есть только у вагонов, которые едут в поезде рейса. */
const normOf = (number: string) => norm.value?.wagons.find((n) => n.number === number) ?? null

// --- рейс ---

async function chooseTrain() {
  const trip = crew.value!.trip
  const saved = await openDialog<TripRow>(TripNewDialog, {
    trip: { id: trip.id, route: trip.route, plannedDt: trip.plannedDt, train: null },
  })
  if (saved) await load()
}

// окно рейса снова открыто после перехода к новому маршруту или поезду: сохранили — формирование заново
useTripDialogReturn(() => void load())

/** Исправить рейс — маршрут, плановое отправление и поезд — в окне «Редактирование рейса». */
async function editTrip() {
  const trip = crew.value!.trip
  const saved = await openDialog<TripRow>(TripNewDialog, {
    trip: { id: trip.id, route: trip.route, plannedDt: trip.plannedDt, train: trip.train?.id ?? null },
  })
  if (saved) await load()
}

/** Ответственные за вагоны — в диалоге; сохранили — формирование заново: проводники вагонов могли смениться. */
async function editResponsibles() {
  const saved = await openDialog<boolean>(TripResponsiblesDialog, { tripId: tripId.value })
  if (saved) await load()
}

const couple = (kind: string) => void router.push({ name: 'trip-coupling', params: { id: props.id, kind } })

const atStation = (w: CrewWagon) =>
  w.locationType === LocationType.STATION && w.place === crew.value!.trip.departureStation
/** Вагон поезда рейса до отправления не на станции отправления: его нужно передать туда. */
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

// --- отметки ---

// отменить отметку — кнопкой в шапке, без окна, как на телефоне: 10 минут после отметки; ошибка — над экраном
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
  gap: 1.3rem;
  overflow-y: auto;
}

.trip-form__section {
  display: flex;
  flex-direction: column;
  gap: 0.8rem;
}

.trip-form__head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.8rem;
}

.trip-form__caption {
  color: $zs-text-muted;
  font-size: 0.9rem;
  font-weight: 600;
}

.trip-form__name {
  color: $zs-text;
  font-weight: 600;
}

.trip-form__table {
  border: 0.0625rem solid $zs-border;
  border-radius: 0.3rem;
  background: #fff;
}

.trip-form__row {
  display: grid;
  grid-template-columns: 2rem 8rem minmax(9rem, 1fr) minmax(16rem, 1.6fr) minmax(16rem, 1.4fr) 1.4rem;
  align-items: center;
  gap: 0.8rem;
  border-top: 0.0625rem solid $zs-border-row;
  font-size: 0.8rem;

  &--head {
    border-top: 0;
    background: $zs-bg-header;
    color: $zs-text-muted;
    font-weight: 600;
  }

  // вагон не едет в поезде рейса: прицепится в пути или отцеплен
  &--off > span {
    opacity: 0.6;
  }
}

.trip-form__number {
  font-weight: 700;
}

.trip-form__changes {
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
}

.trip-form__menu {
  border: 0;
  background: none;
  color: $zs-text-muted;
  cursor: pointer;
  opacity: 0.6;

  &:hover {
    opacity: 1;
  }
}

// черта во всю ширину панели: атрибуты и «Редактировать» — над ней
.trip-form__divider {
  margin-right: -1.5rem;
  margin-bottom: 1rem;
  margin-left: -1.5rem;
  border-top: 0.0625rem solid $zs-border;
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
</style>
