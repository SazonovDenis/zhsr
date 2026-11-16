<template>
  <FormDialog
    v-model:show="show"
    title="Прицепить вагоны"
    ok-text="Прицепить"
    width="70rem"
    hint="Фактом прицепляется вагон, который стоит на станции прицепки (на станции, в её депо или парке отстоя); другие вагоны — только планом."
    :error="error"
    @ok="save"
  >
    <FormSection columns="max-content max-content max-content">
      <FormField label="Прицепка">
        <CbSegment v-model="mode" single required :values="modes" />
      </FormField>
      <!-- план в рейсе — на остановке маршрута; план вне рейса — станция и время -->
      <FormField v-if="mode === 'plan' && stops.length > 0" label="Остановка">
        <CbList v-model="stopIndex" single no-search :values="stopChoices" style="width: 18rem" />
      </FormField>
      <template v-else-if="mode === 'plan'">
        <FormField label="Станция">
          <CbList
            v-model="planStation"
            single
            placeholder="Станция"
            :values="dictChoices('Station')"
            :source="dictSource('Station')"
            style="width: 14rem"
          />
        </FormField>
        <FormField label="Плановое время">
          <DatePicker v-model="planTime" with-time date-text="Плановое время" />
        </FormField>
      </template>
      <template v-else>
        <!-- у поезда без вагонов или с вагонами на разных станциях станцию прицепки выбирает пользователь -->
        <FormField v-if="!factStop && station === null" label="Станция">
          <CbList
            v-model="planStation"
            single
            placeholder="Станция"
            :values="dictChoices('Station')"
            :source="dictSource('Station')"
            style="width: 14rem"
          />
        </FormField>
        <FormField label="Время прицепки">
          <DatePicker v-model="factTime" with-time date-text="Время прицепки" />
        </FormField>
      </template>
    </FormSection>

    <FilterBar>
      <TextInput v-model="filter.number" placeholder="Номер вагона" width="8.8rem" />
      <!-- поиск по номеру отменяет фильтры: находит вагон на любой станции -->
      <span v-if="searching" class="attach-wagons__note">{{ t('При поиске по номеру фильтры не применяются.') }}</span>
      <template v-else>
        <CbList
          v-model="filter.station"
          single
          placeholder="Станция"
          :values="dictChoices('Station')"
          :source="dictSource('Station')"
          style="width: 14rem"
        />
        <CbButton
          v-model="filter.locationKinds"
          label="Расположение"
          :values="dictChoices('LocationKind')"
          :max-button-count="4"
        />
        <CbButton
          v-model="filter.states"
          label="Состояние"
          :values="dictChoices('WagonState')"
          :hidden="[WagonStatus.OUT_OF_SERVICE]"
          :max-button-count="5"
        />
      </template>
      <template #right>{{ t('Отмечено: {n}', { n: picked.size }) }}</template>
    </FilterBar>

    <DataTable
      :columns="columns"
      :rows="rows"
      :error="loadError"
      :retry="load"
      row-key="id"
      :sort="sort"
      :loading="busy.active.value"
      empty="Нет вагонов по выбранному фильтру"
      class="attach-wagons__table"
      @sort="sort = $event"
      @row-click="toggle($event as WagonRow)"
    />
  </FormDialog>
</template>

<script setup lang="ts">
import { date } from 'quasar'
import { computed, provide, reactive, ref, shallowRef, watch } from 'vue'

import type { WagonBusy } from '@/api/trains'
import { fetchWagonBusy } from '@/api/trains'
import type { WagonFilter, WagonRow } from '@/api/wagons'
import { fetchWagons, wagonQuery } from '@/api/wagons'
import { t } from '@/boot/i18n'
import { provideBusy } from '@/shared/busy'
import type { ChoiceItem } from '@/shared/components/choice/choice'
import CbButton from '@/shared/components/choice/CbButton.vue'
import CbList from '@/shared/components/choice/CbList.vue'
import CbSegment from '@/shared/components/choice/CbSegment.vue'
import FormDialog from '@/shared/components/dialog/FormDialog.vue'
import FilterBar from '@/shared/components/FilterBar.vue'
import FormField from '@/shared/components/form/FormField.vue'
import FormSection from '@/shared/components/form/FormSection.vue'
import TextInput from '@/shared/components/form/TextInput.vue'
import DatePicker from '@/shared/components/period/DatePicker.vue'
import DataTable from '@/shared/components/table/DataTable.vue'
import type { TableSort } from '@/shared/components/table/types'
import { defineColumns } from '@/shared/components/table/types'
import { LocationType, WagonStatus } from '@/shared/constants/systemDicts'
import { dictChoices, dictSource } from '@/shared/dict/dictChoices'
import { dictManager } from '@/shared/dict/DictManager'
import type { ShownError } from '@/shared/errors'
import { formError, shownError } from '@/shared/errors'
import { formatDateTime, isoDateTime } from '@/shared/format'
import { placeStation, wagonTransferPlace } from '@/shared/transferPlace'
import { tripRefDatedText } from '@/shared/trips/tripRefText'
import CellWagonLocation from '../wagons/cells/CellWagonLocation.vue'
import CellWagonLocationAt from '../wagons/cells/CellWagonLocationAt.vue'
import CellWagonState from '../wagons/cells/CellWagonState.vue'
import CellWagonStateAt from '../wagons/cells/CellWagonStateAt.vue'
import CellWagonType from '../wagons/cells/CellWagonType.vue'
import CellTripWagonPick from '../trips/cells/CellTripWagonPick.vue'
import type { WagonFit } from '../trips/wagonPick'
import { WAGON_PICK } from '../trips/wagonPick'
import type { AttachChoice, AttachStop } from './attach'

/**
 * Диалог «Прицепить вагоны» (Ф-ПОЕ-02, Ф-РЕЙ-02): прицепка фактом или планом и список вагонов как основной
 * (GET /wagons), по умолчанию — вагоны станции прицепки. Кроме текущих расположения и состояния — они же на момент at.
 * Вагон, занятый в поезде на это время, не выбирается: под номером — в каком поезде и рейсе он занят и с какого по какое
 * время. Фактом выбирается только вагон на станции прицепки (на станции, в её депо или парке отстоя); другие вагоны —
 * только планом. Поиск по номеру находит вагон на любой станции. Открывается через openDialog, результат —
 * AttachChoice.
 *
 * @prop trainId   поезд
 * @prop station   станция прицепки фактом и станция фильтра — id справочника Station; null — не задана
 * @prop at        момент для расположения, состояния и занятости вагонов, ISO
 * @prop stops     остановки рейса для плана: станция отправления и остановки в пути; пусто — прицепка вне рейса
 * @prop factStop  остановка рейса для факта (станция отправления); null — вне рейса
 * @prop factAllowed прицепка фактом возможна
 */
const props = withDefaults(
  defineProps<{
    trainId: number
    station: number | null
    at: string
    stops?: AttachStop[]
    factStop?: AttachStop | null
    factAllowed?: boolean
  }>(),
  { stops: () => [], factStop: null, factAllowed: true },
)

const show = defineModel<boolean>('show', { default: false })
// ожидание диалога: спиннер на таблице — на всю цепочку загрузок
const busy = provideBusy()
const emit = defineEmits<{ done: [choice: AttachChoice] }>()

const modes = computed<ChoiceItem[]>(() => [
  { id: 'fact', text: 'Фактом', disabled: !props.factAllowed },
  { id: 'plan', text: 'Планом' },
])
const mode = ref<string>(props.factAllowed ? 'fact' : 'plan')
const factTime = ref(date.formatDate(new Date(), 'YYYY-MM-DDTHH:mm'))
const planTime = ref<string | null>(date.formatDate(props.at, 'YYYY-MM-DDTHH:mm'))
const planStation = ref<number | null>(props.station)
const stopIndex = ref<number | null>(props.stops.length > 0 ? 0 : null)

const stopChoices = computed<ChoiceItem[]>(() =>
  props.stops.map((s, i) => ({
    id: i,
    text: `${dictManager.getValue('Station', s.station)} • ${formatDateTime(s.time)}`,
  })),
)

/** Станция прицепки сейчас: факт — станция поезда, план — станция остановки или выбранная. */
const targetStation = computed<number | null>(() => {
  if (mode.value === 'fact') return props.factStop?.station ?? props.station ?? planStation.value
  if (props.stops.length > 0) return stopIndex.value === null ? null : (props.stops[stopIndex.value]?.station ?? null)
  return planStation.value
})

/** Момент прицепки: от него считается занятость вагонов. */
const moment = computed<string>(() => {
  if (mode.value === 'fact') return isoDateTime(new Date(factTime.value))
  if (props.stops.length > 0 && stopIndex.value !== null) return props.stops[stopIndex.value]?.time ?? props.at
  return planTime.value ? isoDateTime(new Date(planTime.value)) : props.at
})

const filter = reactive<WagonFilter>({
  number: '',
  station: props.station,
  trip: null,
  locationKinds: [],
  states: [],
  at: props.at,
})
const searching = computed(() => filter.number.trim() !== '')
const sort = ref<TableSort | null>(null)

const rows = shallowRef<WagonRow[]>([])
const busyOf = shallowRef(new Map<string, WagonBusy>())
const loadError = ref<ShownError | null>(null)
const error = ref<ShownError | null>(null)
// отмеченные вагоны — по номеру: отметка сохраняется при смене фильтра
const picked = ref(new Set<string>())

const columns = defineColumns<WagonRow>([
  { field: 'number', title: 'Номер', width: '13rem', cell: CellTripWagonPick, sortable: true },
  { field: 'wagonType', title: 'Тип', width: 'minmax(min-content, 0.67fr)', cell: CellWagonType },
  { field: 'locationType', title: 'Расположение', cell: CellWagonLocation, sortable: true, sortField: 'dbegLocation' },
  { field: 'wagonStatusType', title: 'Состояние', cell: CellWagonState, sortable: true, sortField: 'dbegStatus' },
  { field: 'locationAt', title: 'Расположение на момент', cell: CellWagonLocationAt },
  { field: 'wagonStatusTypeAt', title: 'Состояние на момент', cell: CellWagonStateAt },
])

/** Занятость словами: «поезд Тулпар-1, рейс 002 … от 12.11, с 12.11 08:00 по 14.11 21:00». */
function busyText(b: WagonBusy): string {
  const trip = b.trip ? `, ${t('рейс {trip}', { trip: tripRefDatedText(b.trip) })}` : ''
  const until = b.dend ? ` ${t('по {time}', { time: formatDateTime(b.dend) })}` : ''
  return `${t('Занят: поезд {train}', { train: b.train.name })}${trip}, ${t('с {time}', { time: formatDateTime(b.dbeg) })}${until}`
}

/**
 * Подходит ли вагон: занят в поезде — нельзя; факт — только вагон на станции прицепки, не в рейсе; план — вагон не на
 * станции к моменту прицепки — с предупреждением.
 */
function fit(w: WagonRow): WagonFit {
  const b = busyOf.value.get(w.number)
  if (b) return { block: b.train.id === props.trainId ? t('Уже в поезде') : busyText(b), warn: '' }
  const station = targetStation.value
  if (mode.value === 'fact') {
    const place = wagonTransferPlace(w.locationType, w.place)
    const at = place ? placeStation(place) : null
    if (w.locationType === LocationType.TRIP || at === null || at !== station)
      return { block: t('Не на станции прицепки: только планом'), warn: '' }
    return { block: '', warn: '' }
  }
  const at = w.locationAt ? wagonTransferPlace(w.locationAt.locationType, w.locationAt.place) : null
  const atStation = at ? placeStation(at) : null
  return { block: '', warn: atStation === station ? '' : t('Не на станции прицепки к этому времени') }
}

function toggle(w: WagonRow) {
  if (fit(w).block) return
  const next = new Set(picked.value)
  if (next.has(w.number)) next.delete(w.number)
  else next.add(w.number)
  picked.value = next
}

provide(WAGON_PICK, { isPicked: (w) => picked.value.has(w.number), toggle, fit })

async function load() {
  loadError.value = null
  try {
    await busy.run(async () => {
      const list = await fetchWagons(wagonQuery({ ...filter, at: moment.value }), sort.value)
      const busyList = await fetchWagonBusy(
        list.map((w) => w.number),
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

watch([filter, sort, moment], load, { deep: true })

// отмеченный вагон, который при смене вида или времени перестал подходить, снимается
watch([mode, moment, busyOf], () => {
  picked.value = new Set(
    [...picked.value].filter((n) => {
      const row = rows.value.find((w) => w.number === n)
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

function save() {
  if (picked.value.size === 0) {
    error.value = formError(t('Отметьте вагоны'))
    return
  }
  const fact = mode.value === 'fact'
  const stop = fact ? props.factStop : props.stops.length > 0 ? props.stops[stopIndex.value ?? 0] : null
  if (!fact && !stop && (!planStation.value || !planTime.value)) {
    error.value = formError(t('Укажите станцию и плановое время'))
    return
  }
  if (fact && !stop && (props.station ?? planStation.value) === null) {
    error.value = formError(t('Укажите станцию прицепки'))
    return
  }
  emit('done', {
    numbers: [...picked.value],
    fact,
    time: fact ? isoDateTime(new Date(factTime.value)) : stop ? null : isoDateTime(new Date(planTime.value!)),
    station: stop ? null : fact ? (props.station ?? planStation.value) : planStation.value,
    routeStation: stop?.routeStation ?? null,
  })
}
</script>

<style scoped lang="scss">
.attach-wagons__note {
  color: $zs-text-muted;
  font-size: 0.8rem;
}

.attach-wagons__table {
  height: 26rem;
}
</style>
