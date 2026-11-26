<template>
  <div class="trip-board column no-wrap">
    <!-- шапки обеих таблиц — одним рядом: таблицы начинаются вровень -->
    <div class="trip-board__heads row no-wrap items-center q-col-gutter-x-md q-mb-sm">
      <div class="col-6">
        <div class="trip-board__title">{{ t('По вагонам') }}</div>
      </div>
      <!-- подпись и переключатель — одной строкой -->
      <div class="col-6 row no-wrap items-center q-gutter-x-md">
        <div class="trip-board__title">{{ t('По сотрудникам') }}</div>
        <CbSegment v-model="mode" single required :values="modes" />
      </div>
    </div>
    <div class="trip-board__tables row no-wrap q-col-gutter-x-md">
      <div class="trip-board__side col-6 column no-wrap">
        <DataTable
          :columns="wagonColumns"
          :rows="wagonRows"
          row-key="id"
          empty="В поезде рейса нет вагонов"
          class="trip-board__table"
          @row-click="pickUsers($event as WagonLine)"
        />
      </div>
      <div class="trip-board__side col-6 column no-wrap">
        <DataTable
          :columns="personColumns"
          :rows="personRows"
          row-key="id"
          :empty="mode === 'conductors' ? 'Бригада рейса не назначена' : 'Нет сотрудников станции'"
          class="trip-board__table"
          @row-click="pickWagons($event as PersonLine)"
        />
      </div>
    </div>
  </div>
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
import DataTable from '@/shared/components/table/DataTable.vue'
import CellEdit from '@/shared/components/table/CellEdit.vue'
import { defineColumns } from '@/shared/components/table/types'
import { dictManager } from '@/shared/dict/DictManager'
import { notifyError } from '@/shared/errors'
import { formatDateTime } from '@/shared/format'
import type { ResponsiblesDraft } from '@/shared/trips/tripResponsibles'
import { draftConductorError, pickedWagonsError } from '@/shared/trips/tripResponsibles'
import { isMe, userName } from '@/shared/user'
import { candidateDuty } from '@/shared/wagons/responsible'
import CellRespPerson from './CellRespPerson.vue'
import CellRespWagon from './CellRespWagon.vue'
import CellRespWagons from './CellRespWagons.vue'
import { pickWagonUsers } from './pickWagonUsers'
import TripWagonsDialog from './TripWagonsDialog.vue'

/**
 * Ответственные за вагоны рейса (Ф-РЕЙ-08) в диалоге «Ответственные за вагоны» — две связанные таблицы рядом.
 * Слева «По вагонам» — вагоны поезда и их ответственные, клик по строке — окно выбора сотрудника с выбором нескольких
 * (pickWagonUsers; при сотрудниках станции — с переключателем «Поездная бригада» / «Сотрудники станции»).
 * Справа «По сотрудникам» — «Поездная бригада» (при сотрудниках станции — переключатель «Поездная бригада» /
 * «Сотрудники станции») — сотрудники и их вагоны, клик по строке — окно выбора вагонов (TripWagonsDialog); у
 * проводника с двумя несоседними вагонами или больше чем с двумя — предупреждение; оно же — в окнах выбора. Обе таблицы
 * показывают один черновик (v-model, вагон — сотрудники): правка в одной сразу видна в другой; записывает вызывающий.
 * readonly — клик по строке ничего не открывает.
 */
const props = withDefaults(defineProps<{ data: TripResponsibles; readonly?: boolean }>(), { readonly: false })

const draft = defineModel<ResponsiblesDraft>({ required: true })

type Mode = 'conductors' | 'station'
const mode = ref<Mode>('conductors')
const modes: ChoiceItem[] = [
  { id: 'conductors', text: 'Поездная бригада' },
  { id: 'station', text: 'Сотрудники станции' },
]

const usrsOf = (wagon: number) => draft.value[wagon] ?? []
const currentOf = (wagon: number) => props.data.wagons.find((w) => w.wagon.id === wagon)?.usrs.map((u) => u.id) ?? []
const sameSet = (a: number[], b: number[]) => a.length === b.length && a.every((x) => b.includes(x))

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

export interface WagonLine {
  id: number
  seq: number
  number: string
  /** тип вагона — название из справочника WagonType */
  type: string
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
      type: dictManager.getValue('WagonType', w.wagon.wagonType),
      responsibles: usrs.length > 0 ? usrs.map(nameOf).join(', ') : t('не назначен'),
      dbeg: changed ? null : w.dbeg,
      changed,
    }
  }),
)

// карандаш в конце строки — правка по клику строки; только просмотр — карандаша нет
const EDIT_COLUMN = { field: 'id', title: '', width: '2.8rem', dense: true, cell: CellEdit } as const

const wagonColumns = computed(() => defineColumns<WagonLine>([...wagonBase, ...(props.readonly ? [] : [EDIT_COLUMN])]))

const wagonBase = defineColumns<WagonLine>([
  { field: 'seq', title: '№', width: '2.2rem', dense: true },
  { field: 'number', title: 'Вагон', width: '9rem', cell: CellRespWagon },
  { field: 'responsibles', title: 'Ответственные' },
  {
    field: 'dbeg',
    title: 'С какого времени',
    width: '10rem',
    format: (v, row) => (row.changed ? t('изменено') : v ? formatDateTime(v as string) : '—'),
  },
])

export interface PersonLine {
  id: number
  name: string
  /** текущий пользователь — плашка «вы» */
  me: boolean
  /** должность в бригаде; не в бригаде — служба («депо станции») */
  duty: string
  wagons: string
  /** нарушение правила «один вагон или два соседних» у проводника; пусто — нет */
  wagonsError: string
}

const personRows = computed<PersonLine[]>(() => {
  const staff = mode.value === 'station'
  return (
    props.data.candidates
      // член бригады — в «Поездная бригада», даже если он и сотрудник станции
      .filter((c) => (staff ? c.depotStaff && c.crewDutyType === null : c.crewDutyType !== null))
      .map((c) => ({
        id: c.id,
        name: userName(c),
        me: isMe(c),
        duty: candidateDuty(c),
        wagons:
          props.data.wagons
            .filter((w) => usrsOf(w.wagon.id).includes(c.id))
            .map((w) => w.wagon.number)
            .join(', ') || '—',
        wagonsError: draftConductorError(props.data, draft.value, c.id),
      }))
  )
})

const personColumns = computed(() =>
  defineColumns<PersonLine>([
    { field: 'name', title: 'Сотрудник', cell: CellRespPerson },
    { field: 'wagons', title: 'Вагоны', cell: CellRespWagons },
    ...(props.readonly ? [] : [EDIT_COLUMN]),
  ]),
)

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
  await pickWagonUsers(
    props.data,
    line.number,
    usrsOf(line.id).map((id) => ({ id, name: nameOf(id) })),
    (usrs) => {
      draft.value = { ...draft.value, [line.id]: usrs }
      return Promise.resolve()
    },
    draft.value,
  )
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
.trip-board {
  min-height: 0;
  flex: 1;
}

.trip-board__tables {
  min-height: 0;
  flex: 1;
}

.trip-board__side {
  min-height: 0;
}

// переключатель в шапке таблиц — пониже обычного поля
.trip-board__heads {
  --zs-control-height: 2.1rem;
}

.trip-board__title {
  color: $zs-text-muted;
  font-size: 0.8rem;
  font-weight: 600;
}

.trip-board__table {
  min-height: 0;
  flex: 1;
}
</style>
