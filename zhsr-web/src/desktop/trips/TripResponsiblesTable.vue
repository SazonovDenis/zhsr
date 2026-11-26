<template>
  <FilterBar>
    <CbSegment v-model="mode" single required :values="modes" />
    <template #right>{{ t('Изменено вагонов: {n}', { n: changedCount }) }}</template>
  </FilterBar>
  <div v-for="w in warnings" :key="w" class="trip-resp__warn">{{ w }}</div>
  <DataTable
    v-if="mode === 'wagons'"
    :columns="wagonColumns"
    :rows="wagonRows"
    row-key="id"
    empty="В поезде рейса нет вагонов"
    class="trip-resp__table"
    @row-click="pickUsers($event as WagonLine)"
  />
  <DataTable
    v-else
    :columns="personColumns"
    :rows="personRows"
    row-key="id"
    :empty="mode === 'conductors' ? 'Бригада рейса не назначена' : 'Нет сотрудников станции'"
    class="trip-resp__table"
    @row-click="pickWagons($event as PersonLine)"
  />
</template>

<script setup lang="ts">
import { computed, ref, shallowRef, watch } from 'vue'

import type { TripResponsibles } from '@/api/trips'
import type { WagonRow } from '@/api/wagons'
import { fetchWagonByNumber } from '@/api/wagons'
import { t } from '@/boot/i18n'
import type { ChoiceItem } from '@/shared/components/choice/choice'
import CbSegment from '@/shared/components/choice/CbSegment.vue'
import { openDialog } from '@/shared/components/dialog/openDialog'
import UserPickDialog from '@/shared/components/dialog/UserPickDialog.vue'
import FilterBar from '@/shared/components/FilterBar.vue'
import DataTable from '@/shared/components/table/DataTable.vue'
import { defineColumns } from '@/shared/components/table/types'
import { notifyError } from '@/shared/errors'
import { formatDateTime } from '@/shared/format'
import type { ResponsiblesDraft } from '@/shared/trips/tripResponsibles'
import {
  candidateWagonWarning,
  draftConductorError,
  draftConductorWarnings,
  pickedWagonsError,
} from '@/shared/trips/tripResponsibles'
import { isMe, userName } from '@/shared/user'
import { responsibleDuty, responsiblePlace } from '@/shared/wagons/responsible'
import { candidatePlace } from './candidatePlace'
import CellRespWagons from './CellRespWagons.vue'
import TripWagonsDialog from './TripWagonsDialog.vue'

/**
 * Ответственные за вагоны рейса (Ф-РЕЙ-08) на десктопе — таблица с переключателем режима: «По вагонам» — вагоны
 * поезда и их ответственные, клик по строке — окно выбора сотрудника с выбором нескольких (UserPickDialog, multiple); «По
 * бригаде» и «Сотрудники станции» — сотрудники и их вагоны, клик по строке — окно выбора вагонов (TripWagonsDialog)
 * по вагонам поезда. Ответственные работают параллельно. v-model — черновик (вагон — сотрудники): окна выбора меняют
 * только его, записывает вызывающий одним временем. Проводники с несоседними вагонами или больше чем с двумя —
 * предупреждения над таблицей, у сотрудника и в окнах выбора. readonly — клик по строке ничего не открывает.
 */
const props = withDefaults(defineProps<{ data: TripResponsibles; readonly?: boolean }>(), { readonly: false })

const draft = defineModel<ResponsiblesDraft>({ required: true })

type Mode = 'wagons' | 'conductors' | 'station'
const mode = ref<Mode>('wagons')
const modes = computed<ChoiceItem[]>(() => [
  { id: 'wagons', text: 'По вагонам' },
  { id: 'conductors', text: 'По бригаде' },
  ...(props.data.candidates.some((c) => c.depotStaff && c.crewDutyType === null)
    ? [{ id: 'station', text: 'Сотрудники станции' }]
    : []),
])

const usrsOf = (wagon: number) => draft.value[wagon] ?? []
const currentOf = (wagon: number) => props.data.wagons.find((w) => w.wagon.id === wagon)?.usrs.map((u) => u.id) ?? []
const sameSet = (a: number[], b: number[]) => a.length === b.length && a.every((x) => b.includes(x))
const warnings = computed(() => draftConductorWarnings(props.data, draft.value))
const changedCount = computed(
  () => props.data.wagons.filter((w) => !sameSet(usrsOf(w.wagon.id), currentOf(w.wagon.id))).length,
)

/** Все известные сотрудники: кого можно назначить и уже назначенные. */
const people = computed(() => {
  const res = new Map<number, { id: number; name: string }>()
  for (const c of props.data.candidates) res.set(c.id, { id: c.id, name: c.name })
  for (const w of props.data.wagons) for (const u of w.usrs) if (!res.has(u.id)) res.set(u.id, u)
  return res
})
const nameOf = (id: number) => {
  const p = people.value.get(id)
  return p ? userName(p) : String(id)
}

interface WagonLine {
  id: number
  seq: number
  number: string
  responsibles: string
  dbeg: string | null
  /** состав изменён и ещё не записан */
  changed: boolean
}

const wagonRows = computed<WagonLine[]>(() =>
  props.data.wagons.map((w) => {
    const usrs = usrsOf(w.wagon.id)
    const changed = !sameSet(usrs, currentOf(w.wagon.id))
    return {
      id: w.wagon.id,
      seq: w.seq,
      number: w.wagon.number,
      responsibles: usrs.length > 0 ? usrs.map(nameOf).join(', ') : t('не назначен'),
      dbeg: changed ? null : w.dbeg,
      changed,
    }
  }),
)

const wagonColumns = defineColumns<WagonLine>([
  { field: 'seq', title: '№', width: '2.2rem', dense: true },
  { field: 'number', title: 'Вагон', width: '9rem', cellClass: 'zs-mono' },
  { field: 'responsibles', title: 'Ответственные' },
  {
    field: 'dbeg',
    title: 'С какого времени',
    width: '10rem',
    format: (v, row) => (row.changed ? t('изменено') : v ? formatDateTime(v as string) : '—'),
  },
])

interface PersonLine {
  id: number
  name: string
  duty: string
  wagons: string
  wagonsError: string
}

const personRows = computed<PersonLine[]>(() => {
  const staff = mode.value === 'station'
  return (
    props.data.candidates
      // член бригады — в «По бригаде», даже если он и сотрудник станции
      .filter((c) => (staff ? c.depotStaff && c.crewDutyType === null : c.crewDutyType !== null))
      .map((c) => ({
        id: c.id,
        name: userName(c),
        duty: staff ? responsiblePlace(c) : responsibleDuty(c),
        wagons:
          props.data.wagons
            .filter((w) => usrsOf(w.wagon.id).includes(c.id))
            .map((w) => w.wagon.number)
            .join(', ') || '—',
        wagonsError: draftConductorError(props.data, draft.value, c.id),
      }))
  )
})

const personColumns = defineColumns<PersonLine>([
  { field: 'name', title: 'Сотрудник' },
  { field: 'duty', title: 'Должность' },
  { field: 'wagons', title: 'Вагоны', cellClass: 'zs-mono', cell: CellRespWagons },
])

// вагоны поезда строками таблицы вагонов — для окна выбора вагонов; грузятся один раз
const wagonList = shallowRef<WagonRow[] | null>(null)
watch(
  () => props.data,
  () => (wagonList.value = null),
)

async function loadWagonList(): Promise<WagonRow[]> {
  if (!wagonList.value) {
    wagonList.value = await Promise.all(props.data.wagons.map((w) => fetchWagonByNumber(w.wagon.number)))
  }
  return wagonList.value
}

/** Сотрудники вагона — в окне выбора сотрудника с выбором нескольких; меняет только черновик. */
async function pickUsers(line: WagonLine) {
  if (props.readonly) return
  await openDialog<{ usrs: number[] }>(UserPickDialog, {
    multiple: true,
    title: t('Ответственные за вагон {n}', { n: line.number }),
    okText: 'Выбрать',
    current: usrsOf(line.id).map((id) => ({ id, name: nameOf(id) })),
    load: () =>
      Promise.resolve(
        props.data.candidates.map((c) => ({
          id: c.id,
          name: c.name,
          me: isMe(c),
          duty: responsibleDuty(c),
          place: candidatePlace(c, candidateWagonWarning(props.data, draft.value, c.id, line.id)),
        })),
      ),
    save: (usrs: number[]) => {
      draft.value = { ...draft.value, [line.id]: usrs }
      return Promise.resolve()
    },
  })
}

/** Вагоны сотрудника — в окне выбора вагонов по вагонам поезда; меняет только черновик. */
async function pickWagons(line: PersonLine) {
  if (props.readonly) return
  let list: WagonRow[]
  try {
    list = await loadWagonList()
  } catch (e) {
    notifyError(e)
    return
  }
  const mine = props.data.wagons.filter((w) => usrsOf(w.wagon.id).includes(line.id)).map((w) => w.wagon.number)
  const numbers = await openDialog<string[]>(TripWagonsDialog, {
    wagons: list,
    picked: mine,
    title: t('Вагоны для {name}', { name: line.name }),
    okText: 'Выбрать',
    check: (picked: string[]) =>
      pickedWagonsError(
        props.data,
        line.id,
        props.data.wagons.filter((w) => picked.includes(w.wagon.number)).map((w) => w.wagon.id),
      ),
  })
  if (!numbers) return
  const next = { ...draft.value }
  for (const w of props.data.wagons) {
    const usrs = usrsOf(w.wagon.id)
    const on = numbers.includes(w.wagon.number)
    if (on && !usrs.includes(line.id)) next[w.wagon.id] = [...usrs, line.id]
    if (!on && usrs.includes(line.id)) next[w.wagon.id] = usrs.filter((u) => u !== line.id)
  }
  draft.value = next
}
</script>

<style scoped lang="scss">
.trip-resp__warn {
  color: $zs-error-fg;
}

.trip-resp__table {
  min-height: 0;
  flex: 1;
}
</style>
