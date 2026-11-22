<template>
  <PwaPage
    :back="goBack"
    :title="
      t(after || data?.tripStatusType === TripStatus.DONE ? 'Ответственные после рейса' : 'Ответственные на рейс')
    "
    :subtitle="subtitle"
    fill
  >
    <!-- прогресс шагов — у нижнего края шапки -->
    <template #bottom>
      <div class="resp__bars q-px-sm">
        <span v-for="i in 2" :key="i" class="resp__bar" :class="{ 'resp__bar--on': i - 1 <= step }" />
      </div>
    </template>
    <ErrorBox v-if="error" :error="error" :retry="data ? null : load" />
    <LoadSpinner v-if="busy.active.value" />

    <!-- шаг 1: кого на какие вагоны -->
    <template v-if="data && step === 0">
      <div v-if="!isOnline" class="resp__muted">{{ t('Для действия нужна связь') }}</div>
      <div v-if="data.readonlyReason" class="resp__muted">{{ data.readonlyReason }}</div>
      <CbSegment v-model="mode" single required :values="modes" />

      <!-- список как выбор вагона в заявках: вагон — строкой выбора вагона (WagonOption), справа его ответственные;
           сотрудник — «Списком ФИО» (UserOption), справа его вагоны; у изменённого вагона — плашка; касание — выбор в
           листе снизу -->
      <!-- прокручивается только список, переключатель над ним и сохранение под ним — на месте -->
      <div class="resp__list resp__list--main">
        <button
          v-for="r in rows"
          :key="r.id"
          type="button"
          class="resp__row q-py-sm q-px-md"
          :disabled="!!data.readonlyReason"
          @click="openPick(r.id)"
        >
          <WagonOption
            v-if="mode === 'wagons'"
            :number="wagonOf(r.id)?.wagon.number ?? ''"
            :type="dictManager.getValue('WagonType', wagonOf(r.id)?.wagon.wagonType ?? 0)"
            :place="r.sub"
            :badge="r.changed ? t('изменено') : ''"
          />
          <UserOption v-else :name="r.title" :me="isMe({ id: r.id })" :duty="dutyOf(r.id)" :place="r.sub" />
        </button>
        <div v-if="rows.length === 0" class="resp__muted q-py-lg q-px-md">{{ t(emptyText) }}</div>
      </div>
    </template>

    <!-- шаг 2: с какого времени и итог — кого назначили и кого сняли -->
    <template v-if="data && step === 1">
      <div v-if="!isOnline" class="resp__muted">{{ t('Для действия нужна связь') }}</div>
      <label class="resp__field">
        <span class="resp__label">{{ t('Переназначить с') }}</span>
        <!-- у плана время — плановое отправление или прибытие, не правится -->
        <fieldset :disabled="data.plan" class="resp__time q-pa-none q-ma-none">
          <DatePicker
            :model-value="shownTime"
            with-time
            date-text="Переназначить с"
            @update:model-value="time = $event"
          />
        </fieldset>
      </label>
      <!-- прокручивается только итог; ФИО — каждое своей строкой, номера вагонов — колонкой -->
      <div class="resp__list resp__list--main q-pa-md">
        <template v-for="g in summary" :key="g.title">
          <div v-if="g.lines.length > 0" class="resp__group">
            <div class="resp__label">{{ t(g.title) }}</div>
            <div class="resp__pairs">
              <template v-for="l in g.lines" :key="l.key">
                <span>{{ l.name }}</span>
                <span>{{ t('вагон {n}', { n: l.number }) }}</span>
              </template>
            </div>
          </div>
        </template>
      </div>
    </template>

    <template #bar>
      <BottomBar :actions="navActions" @action="(key) => (key === 'back' ? back() : next())" />
    </template>

    <!-- выбор сотрудников вагона: подсвечено может быть несколько; строки — «Список ФИО», как назначение проводника в
         формировании -->
    <PickSheet
      v-model:show="userPickOpen"
      multiple
      :title="pickTitle"
      ok-text="Выбрать"
      :options="userOptions"
      :groups="userGroups"
      :selected="0"
      :selected-list="pickSelected"
      @pick-list="applyPick"
    >
      <template #option="{ option }">
        <UserOption
          :name="people.get(option.id)?.name ?? option.name"
          :me="isMe({ id: option.id })"
          :duty="people.get(option.id)?.duty ?? ''"
          :place="people.get(option.id)?.place ?? ''"
        />
      </template>
    </PickSheet>

    <!-- выбор вагонов сотрудника — тем же списком вагонов, что на экране: подсвечено может быть несколько -->
    <BottomSheet v-model:show="wagonPickOpen" :title="pickTitle">
      <div class="resp__list">
        <button
          v-for="w in data?.wagons ?? []"
          :key="w.wagon.id"
          type="button"
          class="resp__row q-py-sm q-px-md"
          :class="{ 'resp__row--on': wagonPicked.includes(w.wagon.id) }"
          @click="toggleWagon(w.wagon.id)"
        >
          <WagonOption :number="w.wagon.number" :type="dictManager.getValue('WagonType', w.wagon.wagonType)" />
        </button>
      </div>
      <template #buttons>
        <q-btn
          unelevated
          outline
          no-caps
          color="ink"
          class="resp__sheet-btn zs-pwa-btn zs-soft-border"
          :label="t('Отмена')"
          @click="wagonPickOpen = false"
        />
        <q-btn
          unelevated
          no-caps
          color="primary"
          class="resp__sheet-btn resp__sheet-btn--primary zs-pwa-btn"
          :label="t('Выбрать')"
          @click="confirmWagons"
        />
      </template>
    </BottomSheet>
  </PwaPage>
</template>

<script setup lang="ts">
import { date, Notify } from 'quasar'
import { computed, ref, shallowRef } from 'vue'
import { useRouter } from 'vue-router'

import type { TripResponsibles } from '@/api/trips'
import { fetchTripCrew, fetchTripResponsibles, saveTripResponsibles } from '@/api/trips'
import { t } from '@/boot/i18n'
import { provideBusy } from '@/shared/busy'
import type { ChoiceItem } from '@/shared/components/choice/choice'
import CbSegment from '@/shared/components/choice/CbSegment.vue'
import ErrorBox from '@/shared/components/ErrorBox.vue'
import LoadSpinner from '@/shared/components/LoadSpinner.vue'
import DatePicker from '@/shared/components/period/DatePicker.vue'
import type { ShownError } from '@/shared/errors'
import { formError, shownError } from '@/shared/errors'
import { TripResponsibleLayer } from '@/shared/constants/TripResponsibleLayer'
import { TripStatus } from '@/shared/constants/systemDicts'
import { isoDateTime } from '@/shared/format'
import { isOnline } from '@/shared/network'
import type { ResponsiblesDraft } from '@/shared/trips/tripResponsibles'
import { currentDraft, draftChanges, factTimeError } from '@/shared/trips/tripResponsibles'
import UserOption from '@/shared/components/UserOption.vue'
import WagonOption from '@/shared/components/WagonOption.vue'
import { dictManager } from '@/shared/dict/DictManager'
import { isMe, userName } from '@/shared/user'
import { candidateDuty, responsibleDuty, responsiblePlace } from '@/shared/wagons/responsible'
import type { PickGroup, PickOption } from '../components/PickSheet.vue'
import PickSheet from '../components/PickSheet.vue'
import BottomSheet from '../components/BottomSheet.vue'
import PwaPage from '../components/PwaPage.vue'
import type { BottomBarAction } from '../components/BottomBar.vue'
import BottomBar from '../components/BottomBar.vue'

/**
 * Ответственные за вагоны рейса на телефоне (Ф-РЕЙ-08) — в 2 шага, как подача заявки. Шаг 1: переключатель «Вагоны»,
 * «Бригада», «Сотрудники станции» и строки — у вагона его ответственные, у сотрудника его вагоны; касание строки —
 * выбор нескольких в листе снизу (PickSheet, несколько подсвеченных; у сотрудников вагона при сотрудниках станции —
 * переключатель «Бригада» / «Сотрудники станции»); «Далее» — когда есть изменения. Шаг 2: «Переназначить с» — время
 * изменения (по умолчанию — сейчас; у плана — плановое отправление или прибытие, не правится) и прокручиваемый итог
 * «Назначены» и «Сняты» — строки «ФИО — вагон N»; «Назад» и «Переназначить»: факт записывается одним временем
 * изменения, план — без времени. after — «Ответственные после рейса»: план с планового
 * прибытия; без after — ответственные на рейс: до отправления план на рейс из бригады, после — факт. Нужна связь.
 */
const props = defineProps<{ id: string; after?: boolean }>()

const router = useRouter()
// общее ожидание страницы: спиннер на месте содержимого — на всю цепочку загрузок
const busy = provideBusy()
const data = shallowRef<TripResponsibles | null>(null)
const draft = ref<ResponsiblesDraft>({})
const subtitle = ref('')
const error = ref<ShownError | null>(null)
const running = ref(false)
// значение DatePicker — время браузера без пояса, на сервер уходит с поясом
const time = ref<string | null>(date.formatDate(new Date(), 'YYYY-MM-DDTHH:mm'))
const changes = computed(() => (data.value ? draftChanges(data.value, draft.value) : []))
const layer = computed(() => (props.after ? TripResponsibleLayer.AFTER : TripResponsibleLayer.TRIP))
// плановые отправление и прибытие рейса
const tripTimes = ref<{ plannedDt: string; arrival: string } | null>(null)

type Mode = 'wagons' | 'conductors' | 'station'
const mode = ref<Mode>('wagons')
const modes = computed<ChoiceItem[]>(() => [
  { id: 'wagons', text: 'Вагоны' },
  { id: 'conductors', text: 'Бригада' },
  ...((data.value?.candidates ?? []).some((c) => c.depotStaff && c.crewDutyType === null)
    ? [{ id: 'station', text: 'Сотрудники станции' }]
    : []),
])
const emptyText = computed(() => {
  if (mode.value === 'wagons') return 'В поезде рейса нет вагонов'
  return mode.value === 'conductors' ? 'Бригада рейса не назначена' : 'Нет сотрудников станции'
})

const usrsOf = (wagon: number) => draft.value[wagon] ?? []
const currentOf = (wagon: number) => data.value?.wagons.find((w) => w.wagon.id === wagon)?.usrs.map((u) => u.id) ?? []
const sameSet = (a: number[], b: number[]) => a.length === b.length && a.every((x) => b.includes(x))

/** Все известные сотрудники: кого можно назначить и уже назначенные. */
const people = computed(() => {
  const res = new Map<number, { id: number; name: string; duty: string; place: string }>()
  for (const c of data.value?.candidates ?? [])
    res.set(c.id, { id: c.id, name: c.name, duty: responsibleDuty(c), place: responsiblePlace(c) })
  for (const w of data.value?.wagons ?? [])
    for (const u of w.usrs) if (!res.has(u.id)) res.set(u.id, { id: u.id, name: u.name, duty: '', place: '' })
  return res
})
const nameOf = (id: number) => {
  const p = people.value.get(id)
  return p ? userName(p) : String(id)
}

/** Строка под ФИО в списке «По бригаде» и «Сотрудники станции» — как в таблице на десктопе. */
function dutyOf(id: number): string {
  const c = (data.value?.candidates ?? []).find((x) => x.id === id)
  return c ? candidateDuty(c) : ''
}

/** Сотрудники режима: бригада рейса или сотрудники станции, которые не в бригаде. */
const persons = computed(() => {
  const staff = mode.value === 'station'
  return (data.value?.candidates ?? []).filter((c) =>
    staff ? c.depotStaff && c.crewDutyType === null : c.crewDutyType !== null,
  )
})

const rows = computed(() => {
  const wagons = data.value?.wagons ?? []
  if (mode.value === 'wagons') {
    return wagons.map((w) => {
      const usrs = usrsOf(w.wagon.id)
      return {
        id: w.wagon.id,
        title: `${w.seq}. ${w.wagon.number}`,
        sub: usrs.length > 0 ? usrs.map(nameOf).join(', ') : t('не назначен'),
        changed: !sameSet(usrs, currentOf(w.wagon.id)),
      }
    })
  }
  return persons.value.map((c) => {
    const mine = wagons.filter((w) => usrsOf(w.wagon.id).includes(c.id))
    return {
      id: c.id,
      title: userName(c),
      sub: mine.length > 0 ? mine.map((w) => w.wagon.number).join(', ') : '—',
      changed: wagons.some((w) => usrsOf(w.wagon.id).includes(c.id) !== currentOf(w.wagon.id).includes(c.id)),
    }
  })
})

// листы выбора: строка, для которой выбирают; у вагона — сотрудники, у сотрудника — вагоны
const userPickOpen = ref(false)
const wagonPickOpen = ref(false)
const pickRow = ref<number | null>(null)
// вагоны, подсвеченные в листе выбора вагонов
const wagonPicked = ref<number[]>([])
// заголовок листа: «Ответственные за вагон 17043» или «Вагоны для Иванов А.»
const pickTitle = computed(() => {
  const row = pickRow.value
  if (row === null) return ''
  if (mode.value === 'wagons') return t('Ответственные за вагон {n}', { n: wagonOf(row)?.wagon.number ?? '' })
  return t('Вагоны для {name}', { name: nameOf(row) })
})
const userOptions = computed<PickOption<number>[]>(() =>
  [...people.value.values()].map((p) => ({ id: p.id, name: userName(p) })),
)
// переключатель «Бригада» / «Сотрудники станции» в листе выбора сотрудников — когда есть сотрудники станции
const crewIds = computed(
  () => new Set((data.value?.candidates ?? []).filter((c) => c.crewDutyType !== null).map((c) => c.id)),
)
const userGroups = computed<PickGroup<number>[]>(() =>
  modes.value.some((m) => m.id === 'station')
    ? [
        { id: 'conductors', text: 'Бригада', filter: (o) => crewIds.value.has(o.id) },
        { id: 'station', text: 'Сотрудники станции', filter: (o) => !crewIds.value.has(o.id) },
      ]
    : [],
)
const pickSelected = computed<number[]>(() => {
  const row = pickRow.value
  if (row === null) return []
  if (mode.value === 'wagons') return usrsOf(row)
  return (data.value?.wagons ?? []).filter((w) => usrsOf(w.wagon.id).includes(row)).map((w) => w.wagon.id)
})

/** Вагон поезда рейса по id. */
const wagonOf = (id: number) => data.value?.wagons.find((w) => w.wagon.id === id)

function openPick(id: number) {
  pickRow.value = id
  if (mode.value === 'wagons') {
    userPickOpen.value = true
    return
  }
  wagonPicked.value = [...pickSelected.value]
  wagonPickOpen.value = true
}

function toggleWagon(id: number) {
  wagonPicked.value = wagonPicked.value.includes(id)
    ? wagonPicked.value.filter((x) => x !== id)
    : [...wagonPicked.value, id]
}

function confirmWagons() {
  wagonPickOpen.value = false
  applyPick(wagonPicked.value)
}

/** Выбор в листе — в черновик: у вагона — его сотрудники, у сотрудника — его вагоны. */
function applyPick(ids: number[]) {
  const row = pickRow.value
  if (row === null) return
  if (mode.value === 'wagons') {
    draft.value = { ...draft.value, [row]: ids }
    return
  }
  const next = { ...draft.value }
  for (const w of data.value?.wagons ?? []) {
    const usrs = usrsOf(w.wagon.id)
    const on = ids.includes(w.wagon.id)
    if (on && !usrs.includes(row)) next[w.wagon.id] = [...usrs, row]
    if (!on && usrs.includes(row)) next[w.wagon.id] = usrs.filter((u) => u !== row)
  }
  draft.value = next
}

// --- шаги ---

const step = ref(0)

/** Время в поле «Переназначить с»: у плана — плановое отправление или прибытие, у факта — введённое. */
const shownTime = computed(() => {
  const times = tripTimes.value
  if (!data.value?.plan || !times) return time.value
  return date.formatDate(new Date(props.after ? times.arrival : times.plannedDt), 'YYYY-MM-DDTHH:mm')
})

/** Итог второго шага: кого назначили и кого сняли — по вагонам в порядке поезда. */
const summary = computed(() => {
  const added: { key: string; name: string; number: string }[] = []
  const removed: { key: string; name: string; number: string }[] = []
  for (const w of data.value?.wagons ?? []) {
    const now = usrsOf(w.wagon.id)
    const was = currentOf(w.wagon.id)
    for (const u of now)
      if (!was.includes(u)) added.push({ key: `${w.wagon.id}-${u}`, name: nameOf(u), number: w.wagon.number })
    for (const u of was)
      if (!now.includes(u)) removed.push({ key: `${w.wagon.id}-${u}`, name: nameOf(u), number: w.wagon.number })
  }
  return [
    { title: 'Назначены:', lines: added },
    { title: 'Сняты:', lines: removed },
  ]
})

const navActions = computed<BottomBarAction[]>(() =>
  step.value === 0
    ? [
        { key: 'back', title: 'Отмена', align: 'left' },
        {
          key: 'next',
          title: 'Далее',
          kind: 'primary',
          disabled: !data.value || !!data.value.readonlyReason || changes.value.length === 0,
        },
      ]
    : [
        { key: 'back', title: 'Назад', align: 'left' },
        {
          key: 'next',
          title: 'Переназначить',
          kind: 'primary',
          loading: running.value,
          disabled: !isOnline.value || (!data.value?.plan && !time.value),
        },
      ],
)

function back() {
  if (step.value === 0) goBack()
  else step.value = 0
}

function next() {
  if (step.value === 0) step.value = 1
  else void save()
}

function goBack() {
  if (window.history.state?.back) router.back()
  else void router.push({ name: 'pwa-home' })
}

async function load() {
  error.value = null
  try {
    const [d, crew] = await busy.run(
      Promise.all([fetchTripResponsibles(Number(props.id), layer.value), fetchTripCrew(Number(props.id))]),
    )
    data.value = d
    draft.value = currentDraft(d)
    subtitle.value = `${crew.trip.trainNumber} • ${crew.trip.name}`
    tripTimes.value = { plannedDt: crew.trip.plannedDt, arrival: crew.trip.arrival }
  } catch (e) {
    error.value = shownError(e)
  }
}

async function save() {
  const d = data.value
  if (!d || (!d.plan && !time.value)) return
  const timeError = d.plan ? '' : factTimeError(time.value)
  if (timeError) {
    error.value = formError(timeError)
    return
  }
  running.value = true
  error.value = null
  try {
    const at = d.plan || !time.value ? null : isoDateTime(new Date(time.value))
    await saveTripResponsibles(d.trip, at, changes.value, layer.value)
    Notify.create({ message: t('Ответственные переназначены'), position: 'bottom' })
    goBack()
  } catch (e) {
    error.value = shownError(e)
  } finally {
    running.value = false
  }
}

void load()
</script>

<style scoped lang="scss">
// список и строки — как список выбора вагона в заявках (WagonModePicker)
.resp__list {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border: 0.0625rem solid $zs-border;
  border-radius: 0.3rem;
  background: #fff;
}

// список экрана занимает оставшуюся высоту и прокручивается; остальные блоки не сжимаются — их min-height: auto
.resp__list--main {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
}

.resp__row {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  min-height: 3.3rem;
  border: 0;
  border-top: 0.0625rem solid $zs-border-row;
  background: #fff;
  color: $zs-text;
  font: inherit;
  text-align: left;
  cursor: pointer;

  &:first-child {
    border-top: 0;
  }

  // подсвеченная строка в листе выбора вагонов — фоном выбранной кнопки CbButton
  &--on {
    background: $zs-selected-bg;
  }
}

.resp__bars {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 0.3rem;
}

.resp__bar {
  height: 0.2rem;
  border-radius: 0.1rem;
  background: $zs-border;

  // пройденные шаги и текущий — цветом кнопки
  &--on {
    background: $primary;
  }
}

.resp__time {
  min-width: 0;
  border: 0;

  &:disabled {
    opacity: 0.6;
  }
}

.resp__group {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;

  & + & {
    margin-top: 1rem;
  }
}

// ФИО — каждое своей строкой, номер вагона — колонкой вровень
.resp__pairs {
  display: grid;
  grid-template-columns: minmax(0, max-content) max-content;
  gap: 0.3rem 1.2rem;
}

.resp__sheet-btn {
  flex: 1;

  &--primary {
    flex: 2;
  }
}

.resp__muted {
  color: $zs-text-muted;
  font-size: 0.8rem;
}

.resp__field {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.resp__label {
  font-size: 0.9rem;
  font-weight: 600;
}
</style>
