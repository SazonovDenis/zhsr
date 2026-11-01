<template>
  <DesktopFrame v-model:floating="panel.floating.value" :right-open="panel.open.value">
    <template #header>
      <h1 class="desktop-frame__title">{{ t('Вагоны') }}</h1>
      <div class="desktop-frame__actions">
        <button type="button" class="zs-btn-primary" @click="addWagon">{{ t('Ввести вагон') }}</button>
      </div>
    </template>

    <FilterBar>
      <TextInput v-model="filter.number" placeholder="Номер вагона" width="8.8rem" />
      <CbList
        v-model="filter.station"
        single
        placeholder="Станция"
        :values="dictChoices('Station')"
        style="width: 8.9rem"
      />
      <CbList v-model="filter.trip" single placeholder="Рейс" :values="tripChoices" style="width: 17.7rem">
        <template #option="{ item }">
          <TripOption :trip="tripOf(item)" :route="tripRouteOf(item)" class="trip-option--list" />
        </template>
      </CbList>
      <CbButton
        v-model="filter.locationKinds"
        label="Расположение"
        :values="dictChoices('LocationKind')"
        :max-button-count="4"
      />
      <CbButton v-model="filter.states" label="Состояние" :values="dictChoices('WagonState')" :max-button-count="5" />
      <template #right>{{ t('{n} вагонов', rows.length) }}</template>
    </FilterBar>

    <DataTable
      v-model:selected="selectedId"
      :columns="wagonsColumns"
      :rows="rows"
      row-key="id"
      :sort="sort"
      :loading="loading"
      empty="Нет вагонов по выбранному фильтру"
      @sort="sort = $event"
    />

    <template #right>
      <WagonPanel :wagon="selected" @close="panel.close" />
    </template>
  </DesktopFrame>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, shallowRef, watch } from 'vue'

import type { Route } from '@/api/routes'
import { fetchRoutes } from '@/api/routes'
import type { Trip } from '@/api/trips'
import { fetchTrips } from '@/api/trips'
import type { Wagon, WagonFilter } from '@/api/wagons'
import { fetchWagons } from '@/api/wagons'
import { t } from '@/boot/i18n'
import CbButton from '@/shared/components/choice/CbButton.vue'
import CbList from '@/shared/components/choice/CbList.vue'
import type { RowKey, TableSort } from '@/shared/components/table/types'
import DataTable from '@/shared/components/table/DataTable.vue'
import FilterBar from '@/shared/components/FilterBar.vue'
import TextInput from '@/shared/components/form/TextInput.vue'
import { openDialog } from '@/shared/components/dialog/openDialog'
import { dictChoices } from '@/shared/dict/dictChoices'
import { dictManager } from '@/shared/dict/DictManager'
import DesktopFrame from '../frame/DesktopFrame.vue'
import { useSidePanel } from '../frame/useSidePanel'
import { tripChoiceItem, tripOf, tripRouteOf } from '../trips/tripChoice'
import TripOption from '../trips/TripOption.vue'
import WagonPanel from './WagonPanel.vue'
import WagonNewDialog from './WagonNewDialog.vue'
import { wagonsColumns } from './wagonsColumns'

/** Список вагонов: фильтры и таблица. */

const filter = reactive<WagonFilter>({ number: '', station: null, trip: null, locationKinds: [], states: [] })

const rows = shallowRef<Wagon[]>([])
const loading = ref(false)
const selectedId = ref<RowKey | null>(null)
// правая панель: рядом со списком на широком окне, поверх него — на узком
const panel = useSidePanel(selectedId)
const sort = ref<TableSort | null>(null)
const selected = computed(() => rows.value.find((row) => row.id === selectedId.value) ?? null)
// фильтр «Рейс»: все рейсы, у каждого — его маршрут (остановки, длина и вид маршрута в строке рейса)
const trips = shallowRef<Trip[]>([])
const routes = shallowRef<Route[]>([])
const tripChoices = computed(() =>
  trips.value.map((trip) => tripChoiceItem(trip, routes.value.find((r) => r.id === trip.route) ?? null)),
)

async function load() {
  loading.value = true
  try {
    rows.value = await fetchWagons(filter, sort.value)
  } finally {
    loading.value = false
  }
}

// фильтр или сортировка изменились — данные загружаются заново
watch([filter, sort], load, { deep: true })

// новый вагон введён — список загружается заново, вагон становится выбранным
async function addWagon() {
  const wagon = await openDialog<Wagon>(WagonNewDialog)
  if (!wagon) return
  await load()
  selectedId.value = wagon.id
}

onMounted(async () => {
  await dictManager.prepareDicts('Station', 'LocationKind', 'WagonState', 'WagonType', 'WagonModel', 'Depot')
  ;[trips.value, routes.value] = await Promise.all([
    fetchTrips({ route: null, station: null, from: null, to: null, states: [] }, null),
    fetchRoutes({ kinds: [], states: [] }, null),
  ])
  await load()
})
</script>
