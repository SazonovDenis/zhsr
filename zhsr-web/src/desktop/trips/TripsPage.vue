<template>
  <DesktopFrame v-model:floating="panel.floating.value" :right-open="panel.open.value">
    <template #header>
      <h1 class="desktop-frame__title">{{ t('Рейсы') }}</h1>
      <div class="desktop-frame__actions">
        <button
          type="button"
          class="zs-btn-primary"
          :disabled="!!createReason"
          :title="createReason || undefined"
          @click="addTrip"
        >
          {{ t('Создать рейс') }}
        </button>
      </div>
    </template>

    <FilterBar>
      <RouteSelect v-model="filter.route" placeholder="Маршрут" compact style="width: 17.7rem" />
      <!-- номер вагона: рейсы, в составе которых вагон; остальные фильтры не отменяет -->
      <TextInput v-model="filter.wagon" placeholder="Номер вагона" width="8.8rem" />
      <CbList
        v-model="filter.station"
        single
        placeholder="Станция следования"
        :values="dictChoices('Station')"
        :source="dictSource('Station')"
        style="width: 14rem"
      />
      <PeriodPicker
        v-model:start-date="filter.from"
        v-model:end-date="filter.to"
        label="Период:"
        clearable
        :presets="presets"
      />
      <CbButton
        :model-value="filter.states"
        label="Состояние"
        :values="dictChoices('TripState')"
        :max-button-count="4"
        @update:model-value="onStates($event as number[])"
      />
      <template #right>{{ t('{n} рейсов', rows.length) }}</template>
    </FilterBar>

    <DataTable
      v-model:selected="selectedId"
      :columns="tripsColumns"
      :rows="rows"
      :error="loadError"
      :retry="init"
      row-key="id"
      :sort="sort"
      :loading="busy.active.value"
      :row-menu="rowMenu"
      empty="Рейсов по выбранному фильтру нет"
      @sort="sort = $event"
      @row-dblclick="openForm"
    />

    <template #right>
      <TripPanel :trip="selected" @close="panel.close" @changed="load" />
    </template>
  </DesktopFrame>
</template>

<script setup lang="ts">
import { computed, reactive, ref, shallowRef, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import type { TripRow, TripFilter } from '@/api/trips'
import { fetchTrips } from '@/api/trips'
import { t } from '@/boot/i18n'
import CbButton from '@/shared/components/choice/CbButton.vue'
import CbList from '@/shared/components/choice/CbList.vue'
import PeriodPicker from '@/shared/components/period/PeriodPicker.vue'
import { actionsMenu } from '@/shared/components/table/rowMenu'
import type { RowKey, RowMenuItem, TableSort } from '@/shared/components/table/types'
import DataTable from '@/shared/components/table/DataTable.vue'
import type { ShownError } from '@/shared/errors'
import { shownError } from '@/shared/errors'
import { openDialog } from '@/shared/components/dialog/openDialog'
import FilterBar from '@/shared/components/FilterBar.vue'
import TextInput from '@/shared/components/form/TextInput.vue'
import { dictChoices, dictSource } from '@/shared/dict/dictChoices'
import { provideBusy } from '@/shared/busy'
import { loadCurrentUser } from '@/shared/user'
import RouteSelect from '../routes/RouteSelect.vue'
import DesktopFrame from '../frame/DesktopFrame.vue'
import { useSidePanel } from '../frame/useSidePanel'
import TripPanel from './TripPanel.vue'
import TripNewDialog from './TripNewDialog.vue'
import { FUTURE_STATES, sidePeriod, sidePresets, statesForPeriod, statesSide } from './tripPeriod'
import { createTripReason, TRIP_ACTION_LABELS, useTripActions } from './tripActions'
import { tripsColumns } from './tripsColumns'

/** Список рейсов: фильтры и таблица. */

// по умолчанию — план и выполняется, неделя вперёд
const router = useRouter()
const route = useRoute()
const filter = reactive<TripFilter & { wagon: string }>({
  route: null,
  station: null,
  ...sidePeriod(statesSide(FUTURE_STATES)),
  states: [...FUTURE_STATES],
  wagon: '',
})

/**
 * Переход «Рейсы вагона» с панели вагона (?wagon=номер): рейсы вагона в состояниях «план» и «выполняется» за всё
 * время — период сброшен, остальные фильтры тоже.
 */
function applyRouteQuery() {
  const wagon = route.query.wagon
  if (typeof wagon !== 'string' || !wagon) return
  Object.assign(filter, { route: null, station: null, from: null, to: null, states: [...FUTURE_STATES], wagon })
  void router.replace({ name: 'trips' })
}
applyRouteQuery()
watch(() => route.query.wagon, applyRouteQuery)

// варианты периода зависят от выбранных состояний
const presets = computed(() => sidePresets(statesSide(filter.states)))

// другая сторона времени — период по умолчанию для неё
function onStates(states: number[]) {
  const side = statesSide(states)
  const changed = side !== statesSide(filter.states)
  filter.states = states
  if (changed) Object.assign(filter, sidePeriod(side))
}

// период целиком в другой стороне времени меняет состояния
watch(
  () => [filter.from, filter.to],
  () => {
    const states = statesForPeriod(filter.from, filter.to, filter.states)
    if (states) filter.states = states
  },
)

function openForm(row: unknown) {
  void router.push({ name: 'trip-form', params: { id: (row as TripRow).id } })
}

// рейс создаёт планировщик вагонов (Ф-РЕЙ-01)
const createReason = computed(() => createTripReason())

// меню строки: те же действия и пояснения, что у кнопок панели рейса, затем действие со списком «Создать рейс»
const { actionsOf } = useTripActions(() => void load())
const rowMenu: RowMenuItem<TripRow>[] = [
  ...actionsMenu(TRIP_ACTION_LABELS, actionsOf),
  { label: 'Создать рейс', separator: true, disabled: () => createReason.value, run: () => void addTrip() },
]

const rows = shallowRef<TripRow[]>([])
// общее ожидание страницы: спиннер на таблице — на всю цепочку загрузок (справочники, фильтры, список)
const busy = provideBusy()
const selectedId = ref<RowKey | null>(null)
// правая панель: рядом со списком на широком окне, поверх него — на узком
const panel = useSidePanel(selectedId)
const sort = ref<TableSort | null>(null)
const selected = computed(() => rows.value.find((row) => row.id === selectedId.value) ?? null)

// ошибка загрузки показывается на месте строк таблицы
const loadError = ref<ShownError | null>(null)

async function load() {
  loadError.value = null
  try {
    rows.value = await busy.run(fetchTrips(filter, sort.value))
  } catch (e) {
    rows.value = []
    loadError.value = shownError(e)
  }
}

// фильтр или сортировка изменились — данные загружаются заново
watch([filter, sort], load, { deep: true })

// рейс создан — список загружается заново, рейс становится выбранным
async function addTrip() {
  const trip = await openDialog<TripRow>(TripNewDialog)
  if (!trip) return
  await load()
  selectedId.value = trip.id
}

// цепочка загрузок — сразу в setup: спиннер виден с первой отрисовки; она же — повтор по клику на ошибку в таблице
async function init() {
  // таблице справочники с сервера не нужны: только строки; пользователь — для кнопки создания
  void loadCurrentUser()
  await load()
}
void init()
</script>
