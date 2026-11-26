<template>
  <FormDialog
    v-model:show="show"
    min-height="xl"
    :title="change ? 'Исправить план смены порядка' : 'Сменить порядок вагонов'"
    ok-text="Сохранить"
    width="70rem"
    hint="Фактом меняется порядок вагонов, которые сейчас в поезде; планом — вагонов ожидаемого состава на время смены порядка. Порядок задают кнопки «Вверх» и «Вниз» слева от вагона."
    :running="running"
    :error="error"
    @ok="save"
  >
    <ChangeWhereFields
      ref="fields"
      :card="card"
      :groups="groups"
      :station="change ? change.station : card.station"
      :fact-allowed="!change"
      :at="at"
      :trip="trip"
      :plan="!!change"
      :initial="initial"
      train-wagon-seq
    />

    <DataTable
      :columns="columns"
      :rows="rows"
      :error="loadError"
      :retry="load"
      row-key="wagon"
      :loading="busy.active.value"
      empty="В поезде нет вагонов на это время"
      class="train-wagon-seq__table"
    />
  </FormDialog>
</template>

<script setup lang="ts">
import { computed, provide, ref, shallowRef, useTemplateRef, watch } from 'vue'

import type { TrainCard, TrainChange } from '@/api/trains'
import type { WagonRow } from '@/api/wagons'
import { fetchWagons } from '@/api/wagons'
import { t } from '@/boot/i18n'
import { provideBusy } from '@/shared/busy'
import FormDialog from '@/shared/components/dialog/FormDialog.vue'
import DataTable from '@/shared/components/table/DataTable.vue'
import { defineColumns } from '@/shared/components/table/types'
import type { ShownError } from '@/shared/errors'
import { formError, shownError } from '@/shared/errors'
import { changeWagons, currentComposition, expectedAt, reorderBy } from '@/shared/trains/trainJournal'
import CellWagonLocation from '../wagons/cells/CellWagonLocation.vue'
import CellWagonLocationAt from '../wagons/cells/CellWagonLocationAt.vue'
import CellWagonState from '../wagons/cells/CellWagonState.vue'
import CellWagonType from '../wagons/cells/CellWagonType.vue'
import type { AttachChoice, StopGroup } from './attach'
import CellTrainWagonSeqMove from './cells/CellTrainWagonSeqMove.vue'
import type { ChangeInitial } from './changeWhere'
import ChangeWhereFields from './ChangeWhereFields.vue'
import { TRAIN_WAGON_SEQ_MOVE } from './trainWagonSeqMove'

/** Вагон состава в новом порядке: строка списка вагонов (расположение сейчас и на момент) и новая позиция. */
interface SeqRow extends WagonRow {
  wagon: number
  seq: number
}

/**
 * Диалог «Сменить порядок вагонов» (Ф-ПОЕ-02) — смена порядка фактом или планом, размером и видом как «Отцепить
 * вагоны»: где и когда — по тому, где поезд (ChangeWhereFields), ниже — все вагоны состава; кнопки «Вверх» и «Вниз»
 * слева двигают вагон, колонка «№» — новая позиция. Факт — вагоны текущего состава, план — ожидаемого состава на
 * плановое время. Смена вида или времени сохраняет заданный взаимный порядок вагонов. С записью change — исправление
 * ожидающего плана: поля и порядок — из плана. Открывается через openDialog, результат — поезд после изменения.
 *
 * @prop card   поезд
 * @prop groups рейсы поезда с остановками (loadStopGroups)
 * @prop at     плановое время по умолчанию, ISO
 * @prop trip   рейс, предложенный для плана первым; null — вне рейса
 * @prop change исправляемый план смены порядка; null — новая смена порядка
 * @prop submit сохранить по выбору окна (wagons — все вагоны в новом порядке): поезд после изменения; ошибку окно
 *              показывает у себя и не закрывается
 */
const props = withDefaults(
  defineProps<{
    card: TrainCard
    groups: StopGroup[]
    at: string
    trip?: number | null
    change?: TrainChange | null
    submit: (choice: AttachChoice) => Promise<TrainCard>
  }>(),
  { trip: null, change: null },
)

const show = defineModel<boolean>('show', { default: false })
const emit = defineEmits<{ done: [card: TrainCard] }>()
// ожидание диалога: спиннер на таблице
const busy = provideBusy()

const initial: ChangeInitial | null = props.change
  ? {
      time: props.change.dt,
      trip: props.change.trip?.id ?? null,
      routeStation: props.change.routeStation,
      station: props.change.station,
      seq: null,
      comment: props.change.comment ?? '',
      wagon: 0,
    }
  : null

const fields = useTemplateRef<InstanceType<typeof ChangeWhereFields>>('fields')
const error = ref<ShownError | null>(null)
const loadError = ref<ShownError | null>(null)
const running = ref(false)

const mode = computed(() => fields.value?.state.mode ?? (props.change ? 'plan' : 'fact'))
/** Момент смены порядка: от него — ожидаемый состав и расположение на момент. */
const moment = computed<string>(() => fields.value?.moment ?? props.change?.dt ?? props.at)

// вагоны поезда строками списка вагонов на момент смены порядка
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

/** Состав до смены порядка: факт — текущий, план — ожидаемый на время смены порядка. */
const composition = computed(() =>
  mode.value === 'fact' ? currentComposition(props.card) : expectedAt(props.card, moment.value),
)
const compositionIds = computed(() => composition.value.map((m) => m.wagon.wagon))

/** Порядок, заданный пользователем или исправляемым планом: id вагонов; null — порядок состава. */
const chosen = shallowRef<number[] | null>(props.change ? changeWagons(props.change) : null)
/** Новый порядок вагонов состава. */
const order = computed(() =>
  chosen.value ? reorderBy(compositionIds.value, (id) => id, chosen.value) : compositionIds.value,
)

const rows = computed<SeqRow[]>(() => {
  const members = new Map(composition.value.map((m) => [m.wagon.wagon, m.wagon]))
  return order.value.map((id, i) => {
    const wagon = members.get(id)!
    // вагона нет в ответе (не виден по роли) — поля вагона поезда, без расположения на момент
    const row = wagonRows.value.get(wagon.number) ?? ({ ...wagon, locationAt: null } as unknown as WagonRow)
    return { ...row, id, wagon: id, seq: i + 1 }
  })
})

provide(TRAIN_WAGON_SEQ_MOVE, {
  canMove: (wagon, step) => {
    const to = order.value.indexOf(wagon) + step
    return to >= 0 && to < order.value.length
  },
  move: (wagon, step) => {
    const next = [...order.value]
    const from = next.indexOf(wagon)
    next.splice(from, 1)
    next.splice(from + step, 0, wagon)
    chosen.value = next
    // перестановка — ввод: убирает ошибку диалога
    error.value = null
  },
})

// кнопки перестановки — первой колонкой; остальные колонки и их ширины — как в «Отцепить вагоны»
const columns = defineColumns<SeqRow>([
  { field: 'wagon', title: '', width: '4.5rem', cell: CellTrainWagonSeqMove },
  { field: 'seq', title: '№', width: '3rem' },
  { field: 'number', title: 'Номер', width: '8ch', cellClass: 'zs-mono' },
  { field: 'wagonType', title: 'Тип', width: 'minmax(min-content, 0.67fr)', cell: CellWagonType },
  { field: 'locationType', title: 'Расположение', cell: CellWagonLocation },
  { field: 'wagonStatusType', title: 'Состояние', cell: CellWagonState },
  { field: 'locationAt', title: 'Расположение на момент', cell: CellWagonLocationAt },
])

async function save() {
  // у исправляемого плана состав без самого плана знает только сервер: неизменённый порядок проверяет он
  const same = order.value.every((id, i) => id === compositionIds.value[i])
  if (order.value.length < 2 || (!props.change && same)) {
    error.value = formError(t('Порядок вагонов не изменён'))
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
    emit('done', await props.submit({ ...choice, wagons: order.value }))
  } catch (e) {
    error.value = shownError(e)
  } finally {
    running.value = false
  }
}
</script>

<style scoped lang="scss">
// таблица занимает свободную высоту окна
.train-wagon-seq__table {
  flex: 1 1 0;
  min-height: 0;
}
</style>
