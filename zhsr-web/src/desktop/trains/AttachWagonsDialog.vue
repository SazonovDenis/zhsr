<template>
  <FormDialog
    v-model:show="show"
    min-height="xl"
    title="Прицепить вагоны"
    ok-text="Прицепить"
    width="70rem"
    hint="Фактом прицепляется вагон, который стоит на станции прицепки (на станции, в её депо или парке отстоя; в пути — на самой станции остановки); другие вагоны — только планом. Вагоны встают подряд с выбранной позиции, вагоны с неё сдвигаются назад. Прицепка в рейсе вводит вагон в рейс."
    :running="running"
    :error="error"
    @ok="save"
  >
    <ChangeWhereFields
      ref="fields"
      :card="card"
      :groups="groups"
      :station="station"
      :fact-allowed="factAllowed"
      attach
      :at="at"
      :trip="trip"
    />

    <!-- фильтр и список вагонов — с небольшим отступом, меньше промежутка тела диалога -->
    <div class="attach-wagons__list">
      <FilterBar class="attach-wagons__filter">
        <TextInput
          ref="searchField"
          v-model="filter.number"
          placeholder="Номер вагона"
          width="8.8rem"
          @keydown.down.prevent="dataTable?.focus()"
          @keydown.esc="searchEsc($event, filter.number, () => (filter.number = ''))"
        />
        <!-- поиск по номеру отменяет фильтры: находит вагон на любой станции -->
        <span v-if="searching" class="attach-wagons__note">{{
          t('При поиске по номеру фильтры не применяются.')
        }}</span>
        <template v-else>
          <CbButton
            v-model="filter.locationKinds"
            label="Расположение"
            :values="dictChoices('LocationKind')"
            :max-button-count="4"
          />
        </template>
        <template #right>{{ t('Отмечено: {n}', { n: picked.size }) }}</template>
      </FilterBar>

      <DataTable
        ref="dataTable"
        space-pick
        :columns="columns"
        :rows="rows"
        :error="loadError"
        :retry="load"
        row-key="id"
        :sort="sort"
        :loading="busy.active.value"
        empty="Нет вагонов по выбранному фильтру"
        class="attach-wagons__table"
        @keydown.esc="searchEsc($event, filter.number, () => (filter.number = ''))"
        @sort="sort = $event"
        @row-click="toggle($event as WagonRow)"
      />
    </div>
  </FormDialog>
</template>

<script setup lang="ts">
import { computed, provide, reactive, ref, shallowRef, useTemplateRef, watch } from 'vue'

import type { TrainCard, WagonBusy } from '@/api/trains'
import { fetchWagonBusy } from '@/api/trains'
import type { WagonFilter, WagonRow } from '@/api/wagons'
import { fetchWagons, wagonQuery } from '@/api/wagons'
import { t } from '@/boot/i18n'
import { provideBusy } from '@/shared/busy'
import CbButton from '@/shared/components/choice/CbButton.vue'
import FormDialog from '@/shared/components/dialog/FormDialog.vue'
import FilterBar from '@/shared/components/FilterBar.vue'
import TextInput from '@/shared/components/form/TextInput.vue'
import DataTable from '@/shared/components/table/DataTable.vue'
import { useSearchFocus } from '@/shared/composables/searchFocus'
import { searchEsc } from '@/shared/composables/searchEsc'
import type { TableSort } from '@/shared/components/table/types'
import { defineColumns } from '@/shared/components/table/types'
import { LocationType, WagonStatus } from '@/shared/constants/systemDicts'
import { dictChoices } from '@/shared/dict/dictChoices'
import { dictManager } from '@/shared/dict/DictManager'
import type { ShownError } from '@/shared/errors'
import { formError, shownError } from '@/shared/errors'
import { formatDateTime } from '@/shared/format'
import { placeStation, wagonTransferPlace } from '@/shared/transferPlace'
import { tripRefDatedText } from '@/shared/trips/tripRefText'
import CellWagonLocation from '../wagons/cells/CellWagonLocation.vue'
import CellWagonLocationAt from '../wagons/cells/CellWagonLocationAt.vue'
import CellWagonState from '../wagons/cells/CellWagonState.vue'
import CellWagonType from '../wagons/cells/CellWagonType.vue'
import type { AttachFit } from './attachPick'
import { ATTACH_PICK } from './attachPick'
import CellAttachPick from './cells/CellAttachPick.vue'
import CellAttachStateAt from './cells/CellAttachStateAt.vue'
import type { AttachChoice, StopGroup } from './attach'
import ChangeWhereFields from './ChangeWhereFields.vue'

/**
 * Диалог «Прицепить вагоны» (Ф-ПОЕ-02, Ф-РЕЙ-02) — один и тот же из состава поезда и из формирования рейса: как, где и
 * на какую позицию — по тому, где поезд и вагоны (ChangeWhereFields), и список вагонов как основной (GET /wagons):
 * вагоны станции прицепки (станция выбирается одна — в полях окна), все состояния, кроме «Отставлен». Кроме текущих расположения и состояния — они же на момент прицепки. Вагон,
 * занятый в другом поезде на это время по факту или по плану, не выбирается: вместо отметки — замок, в колонке
 * «Состояние на момент» — в каком поезде и рейсе он занят и с какого по какое время. Фактом выбирается только вагон на станции прицепки; другие вагоны — только планом.
 * Поиск по номеру находит вагон на любой станции. Открывается через openDialog, результат — поезд после прицепки.
 *
 * @prop card        поезд: состав для выбора позиции
 * @prop groups      рейсы поезда с остановками (loadStopGroups)
 * @prop station     станция вагонов поезда — id справочника Station; null — не задана
 * @prop at          плановое время по умолчанию и момент для расположения вагонов, ISO
 * @prop factAllowed прицепка фактом возможна
 * @prop trip        рейс, остановки которого предлагаются для плана первыми; null — вне рейса
 * @prop submit      прицепить по выбору окна: поезд после прицепки; ошибку окно показывает у себя и не закрывается
 */
const props = withDefaults(
  defineProps<{
    card: TrainCard
    groups: StopGroup[]
    station: number | null
    at: string
    factAllowed?: boolean
    trip?: number | null
    submit: (choice: AttachChoice) => Promise<TrainCard>
  }>(),
  { factAllowed: true, trip: null },
)

const dataTable = useTemplateRef<{ focus: () => void }>('dataTable')
const searchField = useTemplateRef<InstanceType<typeof TextInput>>('searchField')
useSearchFocus(searchField)
const show = defineModel<boolean>('show', { default: false })
// ожидание диалога: спиннер на таблице — на всю цепочку загрузок
const busy = provideBusy()
const emit = defineEmits<{ done: [card: TrainCard] }>()

const fields = useTemplateRef<InstanceType<typeof ChangeWhereFields>>('fields')
const mode = computed(() => fields.value?.state.mode ?? 'fact')
/** Станция прицепки: факт — станция поезда или остановки в пути, план — станция остановки или выбранная. */
const targetStation = computed<number | null>(() => fields.value?.targetStation ?? props.station)
/** Момент прицепки: от него считается занятость вагонов. */
const moment = computed<string>(() => fields.value?.moment ?? props.at)

// станция — станция прицепки из полей окна (выбор станции один); состояние — все, кроме «Отставлен»
const filter = reactive<WagonFilter>({
  number: '',
  station: props.station,
  trip: null,
  locationKinds: [],
  states: [WagonStatus.IN_IDLE, WagonStatus.IN_TRIP, WagonStatus.IN_REPAIR, WagonStatus.RESERVE],
  at: props.at,
})
watch(targetStation, (station) => (filter.station = station))
const searching = computed(() => filter.number.trim() !== '')
const sort = ref<TableSort | null>(null)

const rows = shallowRef<WagonRow[]>([])
const busyOf = shallowRef(new Map<string, WagonBusy>())
const loadError = ref<ShownError | null>(null)
const error = ref<ShownError | null>(null)
const running = ref(false)
// отмеченные вагоны — по id: отметка сохраняется при смене фильтра
const picked = ref(new Set<number>())

const columns = defineColumns<WagonRow>([
  { field: 'number', title: 'Номер', width: '13rem', cell: CellAttachPick, sortable: true },
  { field: 'wagonType', title: 'Тип', width: 'minmax(min-content, 0.67fr)', cell: CellWagonType },
  { field: 'locationType', title: 'Расположение', cell: CellWagonLocation, sortable: true, sortField: 'dbegLocation' },
  { field: 'wagonStatusType', title: 'Состояние', cell: CellWagonState, sortable: true, sortField: 'dbegStatus' },
  { field: 'locationAt', title: 'Расположение на момент', cell: CellWagonLocationAt },
  { field: 'wagonStatusTypeAt', title: 'Состояние на момент', cell: CellAttachStateAt },
])

/** Занятость словами: «поезд Тулпар-1, рейс 002 … от 12.11, с 12.11 08:00 по 14.11 21:00». */
function busyText(b: WagonBusy): string {
  const trip = b.trip ? `, ${t('рейс {trip}', { trip: tripRefDatedText(b.trip) })}` : ''
  const until = b.dend ? ` ${t('по {time}', { time: formatDateTime(b.dend) })}` : ''
  return `${t('Занят: поезд {train}', { train: b.train.name })}${trip}, ${t('с {time}', { time: formatDateTime(b.dbeg) })}${until}`
}

/**
 * Подходит ли вагон: занят в поезде — нельзя (замок вместо отметки, занятость — в колонке «Состояние на момент»); факт — только вагон на станции прицепки, не в рейсе; план — вагон не на
 * станции к моменту прицепки — с предупреждением.
 */
function fit(w: WagonRow): AttachFit {
  const b = busyOf.value.get(w.number)
  if (b) {
    const busy = b.train.id === props.card.id ? t('Уже в поезде') : busyText(b)
    return { block: busy, warn: '', busy }
  }
  const station = targetStation.value
  if (mode.value === 'fact') {
    const place = wagonTransferPlace(w.locationType, w.place)
    const at = place ? placeStation(place) : null
    if (w.locationType === LocationType.TRIP || at === null || at !== station)
      return { block: t('Не на станции прицепки: только планом'), warn: '', busy: '' }
    return { block: '', warn: '', busy: '' }
  }
  const at = w.locationAt ? wagonTransferPlace(w.locationAt.locationType, w.locationAt.place) : null
  const atStation = at ? placeStation(at) : null
  return { block: '', warn: atStation === station ? '' : t('Не на станции прицепки к этому времени'), busy: '' }
}

function toggle(w: WagonRow) {
  if (fit(w).block) return
  const next = new Set(picked.value)
  if (next.has(w.id)) next.delete(w.id)
  else next.add(w.id)
  picked.value = next
  // отметка вагона — ввод: убирает ошибку диалога
  error.value = null
}

provide(ATTACH_PICK, { isPicked: (w) => picked.value.has(w.id), toggle, fit })

async function load() {
  loadError.value = null
  try {
    await busy.run(async () => {
      const list = await fetchWagons(wagonQuery({ ...filter, at: moment.value, plan: mode.value === 'plan' }), sort.value)
      const busyList = await fetchWagonBusy(
        list.map((w) => w.id),
        moment.value,
      )
      rows.value = list
      busyOf.value = new Map(busyList.map((b) => [b.number, b]))
    })
  } catch (e) {
    rows.value = []
    loadError.value = shownError(e)
  }
}

watch([filter, sort, moment, mode], load, { deep: true })

// отмеченный вагон, который при смене вида или времени перестал подходить, снимается
watch([mode, moment, busyOf], () => {
  picked.value = new Set(
    [...picked.value].filter((id) => {
      const row = rows.value.find((w) => w.id === id)
      return !row || !fit(row).block
    }),
  )
})

async function init() {
  try {
    await busy.run(
      dictManager.prepareDicts('Station', 'Depot', 'Yard', 'WagonType', 'WagonModel', 'LocationKind', 'WagonState'),
    )
  } catch (e) {
    loadError.value = shownError(e)
  }
  await load()
}
void init()

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
.attach-wagons__list {
  display: flex;
  flex: 1 1 0;
  flex-direction: column;
  min-height: 0;
}

.attach-wagons__filter {
  margin-bottom: 0.4rem;
}

.attach-wagons__note {
  color: $zs-text-muted;
  font-size: 0.8rem;
}

// таблица занимает свободную высоту окна
.attach-wagons__table {
  flex: 1 1 0;
  min-height: 0;
}
</style>
