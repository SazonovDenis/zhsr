<template>
  <FormDialog
    v-model:show="show"
    title="Добавить вагоны"
    ok-text="Добавить"
    width="70rem"
    hint="Вагоны в депо и парке отстоя перед отправлением нужно передать на станцию."
    :error="error"
    @ok="save"
  >
    <FilterBar>
      <TextInput v-model="filter.number" placeholder="Номер вагона" width="8.8rem" />
      <!-- поиск по номеру отменяет фильтры: находит вагон на любой станции -->
      <span v-if="searching" class="trip-wagons__note">{{ t('При поиске по номеру фильтры не применяются.') }}</span>
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
      class="trip-wagons__table"
      @sort="sort = $event"
      @row-click="toggle($event as WagonRow)"
    />
  </FormDialog>
</template>

<script setup lang="ts">
import { computed, provide, reactive, ref, shallowRef, watch } from 'vue'

import type { WagonFilter, WagonRow } from '@/api/wagons'
import { fetchWagons } from '@/api/wagons'
import { t } from '@/boot/i18n'
import { provideBusy } from '@/shared/busy'
import CbButton from '@/shared/components/choice/CbButton.vue'
import CbList from '@/shared/components/choice/CbList.vue'
import FormDialog from '@/shared/components/dialog/FormDialog.vue'
import FilterBar from '@/shared/components/FilterBar.vue'
import TextInput from '@/shared/components/form/TextInput.vue'
import DataTable from '@/shared/components/table/DataTable.vue'
import type { TableSort } from '@/shared/components/table/types'
import { defineColumns } from '@/shared/components/table/types'
import { WagonStatus } from '@/shared/constants/systemDicts'
import { dictChoices, dictSource } from '@/shared/dict/dictChoices'
import { dictManager } from '@/shared/dict/DictManager'
import type { ShownError } from '@/shared/errors'
import { formError, shownError } from '@/shared/errors'
import { placeStation, wagonTransferPlace } from '@/shared/transferPlace'
import { tripRefDatedText } from '@/shared/trips/tripRefText'
import CellWagonLocation from '../wagons/cells/CellWagonLocation.vue'
import CellWagonLocationAt from '../wagons/cells/CellWagonLocationAt.vue'
import CellWagonState from '../wagons/cells/CellWagonState.vue'
import CellWagonStateAt from '../wagons/cells/CellWagonStateAt.vue'
import CellWagonType from '../wagons/cells/CellWagonType.vue'
import CellTripWagonPick from './cells/CellTripWagonPick.vue'
import type { WagonFit } from './wagonPick'
import { WAGON_PICK } from './wagonPick'

/**
 * Диалог «Добавить вагоны» (Ф-РЕЙ-02): список вагонов как основной (GET /wagons), по умолчанию — станция отправления.
 * Кроме текущих расположения и состояния — они же на момент отправления рейса. Вагон в составе другого рейса «план»
 * или «выполняется» и вагон, уже стоящий в составе этого рейса, не выбираются — причина под номером; вагон, который
 * к отправлению не на станции отправления, — с предупреждением, выбрать можно. Поиск по номеру находит вагон на
 * любой станции. Открывается через openDialog, результат — номера отмеченных вагонов.
 *
 * @prop tripId           рейс
 * @prop departureStation станция отправления — id справочника Station
 * @prop plannedDt        плановое отправление, ISO: момент для расположения и состояния
 */
const props = defineProps<{ tripId: number; departureStation: number; plannedDt: string }>()

const show = defineModel<boolean>('show', { default: false })
// ожидание диалога: спиннер на таблице — на всю цепочку загрузок
const busy = provideBusy()
const emit = defineEmits<{ done: [numbers: string[]] }>()

const filter = reactive<WagonFilter>({
  number: '',
  station: props.departureStation,
  trip: null,
  locationKinds: [],
  states: [],
  at: props.plannedDt,
})
const searching = computed(() => filter.number.trim() !== '')
const sort = ref<TableSort | null>(null)

const rows = shallowRef<WagonRow[]>([])
const loadError = ref<ShownError | null>(null)
const error = ref<ShownError | null>(null)
// отмеченные вагоны — по номеру: отметка сохраняется при смене фильтра
const picked = ref(new Set<string>())

const columns = defineColumns<WagonRow>([
  { field: 'number', title: 'Номер', width: '11rem', cell: CellTripWagonPick, sortable: true },
  { field: 'wagonType', title: 'Тип', width: 'minmax(min-content, 0.67fr)', cell: CellWagonType },
  { field: 'locationType', title: 'Расположение', cell: CellWagonLocation, sortable: true, sortField: 'dbegLocation' },
  { field: 'wagonStatusType', title: 'Состояние', cell: CellWagonState, sortable: true, sortField: 'dbegStatus' },
  { field: 'locationAt', title: 'Расположение на момент отправки', cell: CellWagonLocationAt },
  { field: 'wagonStatusTypeAt', title: 'Состояние на момент отправки', cell: CellWagonStateAt },
])

/** Подходит ли вагон: в другом рейсе или уже в составе — нельзя; не на станции отправления к отправлению — можно. */
function fit(w: WagonRow): WagonFit {
  const own = w.trips.find((tr) => tr.trip.id === props.tripId)
  if (own) return { block: t('Уже в составе рейса'), warn: '' }
  const other = w.trips[0]
  if (other) return { block: t('В составе рейса {trip}', { trip: tripRefDatedText(other.trip) }), warn: '' }
  const at = w.locationAt
  const place = at ? wagonTransferPlace(at.locationType, at.place) : null
  const station = place ? placeStation(place) : null
  return { block: '', warn: station === props.departureStation ? '' : t('Не на станции отправления к отправлению') }
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
    rows.value = await busy.run(fetchWagons(filter, sort.value))
  } catch (e) {
    rows.value = []
    loadError.value = shownError(e)
  }
}

watch([filter, sort], load, { deep: true })

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
  emit('done', [...picked.value])
}
</script>

<style scoped lang="scss">
.trip-wagons__note {
  color: $zs-text-muted;
  font-size: 0.8rem;
}

.trip-wagons__table {
  height: 28rem;
}
</style>
