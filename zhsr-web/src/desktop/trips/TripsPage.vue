<template>
  <DesktopFrame v-model:floating="panel.floating.value" :right-open="panel.open.value">
    <template #header>
      <h1 class="desktop-frame__title">{{ t('Рейсы') }}</h1>
      <div class="desktop-frame__actions">
        <button type="button" class="zs-btn-primary" @click="addTrip">{{ t('Создать рейс') }}</button>
      </div>
    </template>

    <FilterBar>
      <RouteChoice v-model="filter.route" placeholder="Маршрут" compact style="width: 17.7rem" />
      <CbList
        v-model="filter.station"
        single
        placeholder="Станция"
        :values="dictChoices('Station')"
        style="width: 8.9rem"
      />
      <PeriodPicker v-model:start-date="filter.from" v-model:end-date="filter.to" label="Период:" clearable />
      <CbButton v-model="filter.states" label="Состояние" :values="dictChoices('TripState')" :max-button-count="4" />
      <template #right>{{ t('{n} рейсов', rows.length) }}</template>
    </FilterBar>

    <DataTable
      v-model:selected="selectedId"
      :columns="tripsColumns"
      :rows="rows"
      row-key="id"
      :sort="sort"
      :loading="loading"
      empty="Рейсов по выбранному фильтру нет"
      @sort="sort = $event"
    />

    <template #right>
      <TripPanel :trip="selected" @close="panel.close" />
    </template>
  </DesktopFrame>
</template>

<script setup lang="ts">
import { date } from 'quasar'
import { computed, onMounted, reactive, ref, shallowRef, watch } from 'vue'

import type { Trip, TripFilter } from '@/api/trips'
import { fetchTrips } from '@/api/trips'
import { t } from '@/boot/i18n'
import CbButton from '@/shared/components/choice/CbButton.vue'
import CbList from '@/shared/components/choice/CbList.vue'
import PeriodPicker from '@/shared/components/period/PeriodPicker.vue'
import type { RowKey, TableSort } from '@/shared/components/table/types'
import DataTable from '@/shared/components/table/DataTable.vue'
import { openDialog } from '@/shared/components/dialog/openDialog'
import FilterBar from '@/shared/components/FilterBar.vue'
import { dictChoices } from '@/shared/dict/dictChoices'
import { dictManager } from '@/shared/dict/DictManager'
import RouteChoice from '../routes/RouteChoice.vue'
import DesktopFrame from '../frame/DesktopFrame.vue'
import { useSidePanel } from '../frame/useSidePanel'
import TripPanel from './TripPanel.vue'
import TripNewDialog from './TripNewDialog.vue'
import { tripsColumns } from './tripsColumns'

/** Список рейсов: фильтры и таблица. */

// по умолчанию — неделя по сегодняшний день
const isoDate = (d: Date) => date.formatDate(d, 'YYYY-MM-DD')
const filter = reactive<TripFilter>({
  route: null,
  station: null,
  from: isoDate(date.addToDate(new Date(), { days: -6 })),
  to: isoDate(new Date()),
  states: [],
})

const rows = shallowRef<Trip[]>([])
const loading = ref(false)
const selectedId = ref<RowKey | null>(null)
// правая панель: рядом со списком на широком окне, поверх него — на узком
const panel = useSidePanel(selectedId)
const sort = ref<TableSort | null>(null)
const selected = computed(() => rows.value.find((row) => row.id === selectedId.value) ?? null)

async function load() {
  loading.value = true
  try {
    rows.value = await fetchTrips(filter, sort.value)
  } finally {
    loading.value = false
  }
}

// фильтр или сортировка изменились — данные загружаются заново
watch([filter, sort], load, { deep: true })

// рейс создан — список загружается заново, рейс становится выбранным
async function addTrip() {
  const trip = await openDialog<Trip>(TripNewDialog)
  if (!trip) return
  await load()
  selectedId.value = trip.id
}

onMounted(async () => {
  await dictManager.prepareDicts('Station', 'TripState')
  await load()
})
</script>
