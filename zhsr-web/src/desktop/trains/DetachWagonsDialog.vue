<template>
  <FormDialog
    v-model:show="show"
    min-height="xl"
    title="Отцепить вагоны"
    ok-text="Отцепить"
    width="70rem"
    hint="Фактом отцепляются вагоны, которые сейчас в поезде; планом — вагоны ожидаемого состава на время отцепки. Вагоны за отцепленными сдвигаются вперёд. Отцепка в рейсе ставит вагоны на станцию остановки. Отцепка снимает вагоны с проводников в рейсах поезда в статусе «План»."
    :running="running"
    :error="error"
    @ok="save"
  >
    <template v-if="problemRows.length > 0" #warnings>
      <WagonProblemList :rows="problemRows" strip />
    </template>
    <ChangeWhereFields
      ref="fields"
      :card="card"
      :groups="groups"
      :station="card.station"
      :at="at"
      :trip="trip"
      :plan="plan"
    />

    <!-- фильтр и список вагонов — с небольшим отступом, меньше промежутка тела диалога -->
    <div class="detach-wagons__list">
      <FilterBar class="detach-wagons__filter">
        <template #right>{{ t('Отмечено: {n}', { n: picked.size }) }}</template>
      </FilterBar>

      <DataTable
        space-pick
        :columns="columns"
        :rows="rows"
        :error="loadError"
        :retry="load"
        row-key="key"
        :loading="busy.active.value"
        empty="В поезде нет вагонов на это время"
        class="detach-wagons__table"
        @row-click="toggle($event as PickRow)"
      />
      <!-- отмеченные вагоны: что будет мешать их планам, если отцепить -->
      <ErrorBox v-if="problemsError" :error="problemsError" />
    </div>
  </FormDialog>
</template>

<script setup lang="ts">
import { computed, provide, ref, shallowRef, useTemplateRef, watch } from 'vue'

import type { TrainCard } from '@/api/trains'
import type { WagonRow } from '@/api/wagons'
import { fetchWagons } from '@/api/wagons'
import { t } from '@/boot/i18n'
import { provideBusy } from '@/shared/busy'
import ErrorBox from '@/shared/components/ErrorBox.vue'
import FormDialog from '@/shared/components/dialog/FormDialog.vue'
import FilterBar from '@/shared/components/FilterBar.vue'
import DataTable from '@/shared/components/table/DataTable.vue'
import { defineColumns } from '@/shared/components/table/types'
import type { ShownError } from '@/shared/errors'
import { formError, shownError } from '@/shared/errors'
import { TrainWagonChangeType } from '@/shared/constants/TrainWagonChange'
import { currentComposition, expectedAt } from '@/shared/trains/trainJournal'
import { useWagonProblems } from '@/shared/trains/useWagonProblems'
import WagonProblemList from '@/shared/trains/WagonProblemList.vue'
import CellTripWagonPick from '../trips/cells/CellTripWagonPick.vue'
import { WAGON_PICK } from '../trips/wagonPick'
import CellWagonLocation from '../wagons/cells/CellWagonLocation.vue'
import CellWagonLocationAt from '../wagons/cells/CellWagonLocationAt.vue'
import CellWagonState from '../wagons/cells/CellWagonState.vue'
import CellWagonType from '../wagons/cells/CellWagonType.vue'
import type { AttachChoice, StopGroup } from './attach'
import ChangeWhereFields from './ChangeWhereFields.vue'

/** Вагон состава для выбора: строка списка вагонов (расположение сейчас и на момент) и позиция. */
interface PickRow extends WagonRow {
  key: number
  seq: number
}

/**
 * Диалог «Отцепить вагоны» (Ф-ПОЕ-02) — отцепка нескольких вагонов фактом или планом, размером и видом как «Прицепить
 * вагоны»: где и когда — по тому, где поезд (ChangeWhereFields), ниже — вагоны состава с отметкой выбора и колонками
 * «№», «Номер», «Тип», «Расположение», «Состояние», «Расположение на момент» (как в «Прицепить вагоны»; данные — GET /wagons по
 * номерам вагонов поезда на момент отцепки). Факт — вагоны текущего состава, план — ожидаемого состава на плановое
 * время (раньше последнего факта поезда — состав по факту). «Фактом» не блокируется. Из меню вагона окно открывается с
 * этим вагоном, уже отмеченным (wagon). Открывается через openDialog, результат — поезд после отцепки.
 *
 * @prop card   поезд
 * @prop groups рейсы поезда с остановками (loadStopGroups)
 * @prop at     плановое время по умолчанию, ISO
 * @prop trip   рейс, предложенный для плана первым; null — вне рейса
 * @prop wagon  id вагона, отмеченного при открытии; null — ни одного
 * @prop plan   открыть сразу планом
 * @prop submit отцепить по выбору окна: поезд после отцепки; ошибку окно показывает у себя и не закрывается
 */
const props = withDefaults(
  defineProps<{
    card: TrainCard
    groups: StopGroup[]
    at: string
    trip?: number | null
    wagon?: number | null
    plan?: boolean
    submit: (choice: AttachChoice) => Promise<TrainCard>
  }>(),
  { trip: null, wagon: null, plan: false },
)

const show = defineModel<boolean>('show', { default: false })
const emit = defineEmits<{ done: [card: TrainCard] }>()
// ожидание диалога: спиннер на таблице
const busy = provideBusy()

const fields = useTemplateRef<InstanceType<typeof ChangeWhereFields>>('fields')
const error = ref<ShownError | null>(null)
const loadError = ref<ShownError | null>(null)
const running = ref(false)
// отмеченные вагоны — по номеру
const picked = ref(new Set<number>(props.wagon ? [props.wagon] : []))

const mode = computed(() => fields.value?.state.mode ?? (props.plan ? 'plan' : 'fact'))
/** Момент отцепки: от него — ожидаемый состав и расположение на момент. */
const moment = computed<string>(() => fields.value?.moment ?? props.at)

// вагоны поезда строками списка вагонов на момент отцепки
const wagonRows = shallowRef(new Map<string, WagonRow>())

async function load() {
  loadError.value = null
  try {
    await busy.run(async () => {
      const ids = props.card.trainWagons.map((w) => w.wagon)
      const list = ids.length
        ? await fetchWagons(
            { number: '', ids, station: null, trip: null, locationKinds: [], states: [], at: moment.value },
            null,
          )
        : []
      wagonRows.value = new Map(list.map((w) => [w.number, w]))
    })
  } catch (e) {
    loadError.value = shownError(e)
  }
}
watch(moment, load, { immediate: true })

/** Вагоны для выбора: факт — текущий состав, план — ожидаемый на время отцепки. */
const rows = computed<PickRow[]>(() => {
  const list = mode.value === 'fact' ? currentComposition(props.card) : expectedAt(props.card, moment.value)
  return list.map((m) => {
    // вагона нет в ответе (не виден по роли) — поля вагона поезда, без расположения на момент
    const row = wagonRows.value.get(m.wagon.number) ?? ({ ...m.wagon, locationAt: null } as unknown as WagonRow)
    return { ...row, id: m.wagon.wagon, key: m.wagon.wagon, seq: m.state.seq }
  })
})

// вагон, которого нет в составе на новое время или в новом виде, снимается
watch(rows, (list) => {
  const ids = new Set(list.map((r) => r.id))
  picked.value = new Set([...picked.value].filter((id) => ids.has(id)))
})

function toggle(r: Pick<PickRow, 'id'>) {
  const next = new Set(picked.value)
  if (next.has(r.id)) next.delete(r.id)
  else next.add(r.id)
  picked.value = next
  // отметка вагона — ввод: убирает ошибку диалога
  error.value = null
}

const { rows: problemRows, error: problemsError } = useWagonProblems(() => ({
  train: props.card.id,
  wagons: [...picked.value],
  changeType: TrainWagonChangeType.DETACH,
  fact: mode.value === 'fact',
  time: moment.value,
  station: fields.value?.targetStation ?? null,
  routeStation: fields.value?.routeStation ?? null,
  plan: null,
}))

provide(WAGON_PICK, {
  isPicked: (w: WagonRow) => picked.value.has(w.id),
  toggle,
  fit: () => ({ block: '', warn: '' }),
})

// колонки и их ширины — как в «Прицепить вагоны»
const columns = defineColumns<PickRow>([
  { field: 'seq', title: '№', width: '3rem' },
  { field: 'number', title: 'Номер', width: '13rem', cell: CellTripWagonPick },
  { field: 'wagonType', title: 'Тип', width: 'minmax(min-content, 0.67fr)', cell: CellWagonType },
  { field: 'locationType', title: 'Расположение', cell: CellWagonLocation },
  { field: 'wagonStatusType', title: 'Состояние', cell: CellWagonState },
  { field: 'locationAt', title: 'Расположение на момент', cell: CellWagonLocationAt },
])

async function save() {
  if (picked.value.size === 0) {
    error.value = formError(t('Отметьте вагоны'))
    return
  }
  const choice = fields.value!.build()
  if (typeof choice === 'string') {
    error.value = formError(choice)
    return
  }
  error.value = null
  running.value = true
  try {
    emit('done', await props.submit({ ...choice, wagons: [...picked.value] }))
  } catch (e) {
    error.value = shownError(e)
  } finally {
    running.value = false
  }
}
</script>

<style scoped lang="scss">
// фильтр и список вагонов — одним блоком на свободную высоту окна, между ними — небольшой отступ
.detach-wagons__list {
  display: flex;
  flex: 1 1 0;
  flex-direction: column;
  min-height: 0;
}

.detach-wagons__filter {
  margin-bottom: 0.4rem;
}

// таблица занимает свободную высоту окна
.detach-wagons__table {
  flex: 1 1 0;
  min-height: 0;
}
</style>
