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
      <!-- поиск по номеру отменяет фильтры: они скрыты и не применяются, но не очищаются -->
      <span v-if="searching" class="wagons__search-note">{{ t('При поиске по номеру фильтры не применяются.') }}</span>
      <template v-else>
        <CbList
          v-model="filter.station"
          single
          placeholder="Станция"
          :values="dictChoices('Station')"
          :source="dictSource('Station')"
          style="width: 8.9rem"
        />
        <CbList
          v-model="filter.trip"
          single
          placeholder="Рейс"
          :values="tripChoices"
          :source="tripLoad"
          style="width: 17.7rem"
        >
          <template #option="{ item }">
            <TripOptionView :trip="tripOptionOf(item)" class="trip-option--list" />
          </template>
        </CbList>
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
      <template #right>{{ t('{n} вагонов', rows.length) }}</template>
    </FilterBar>

    <DataTable
      v-model:selected="selectedId"
      :columns="wagonsColumns"
      :rows="rows"
      :error="loadError"
      :retry="init"
      row-key="id"
      :sort="sort"
      :loading="busy.active.value"
      empty="Нет вагонов по выбранному фильтру"
      @sort="sort = $event"
    />

    <template #right>
      <WagonPanel :wagon="selected" @close="panel.close" />
    </template>
  </DesktopFrame>
</template>

<script setup lang="ts">
import { computed, reactive, ref, shallowRef, watch } from 'vue'

import type { TripOption } from '@/api/trips'
import { fetchTripOptions } from '@/api/trips'
import type { WagonRow, WagonFilter } from '@/api/wagons'
import { fetchWagons } from '@/api/wagons'
import { t } from '@/boot/i18n'
import CbButton from '@/shared/components/choice/CbButton.vue'
import CbList from '@/shared/components/choice/CbList.vue'
import type { RowKey, TableSort } from '@/shared/components/table/types'
import DataTable from '@/shared/components/table/DataTable.vue'
import type { ShownError } from '@/shared/errors'
import { shownError } from '@/shared/errors'
import { TripStatus, WagonStatus } from '@/shared/constants/systemDicts'
import FilterBar from '@/shared/components/FilterBar.vue'
import TextInput from '@/shared/components/form/TextInput.vue'
import { openDialog } from '@/shared/components/dialog/openDialog'
import { dictChoices, dictSource } from '@/shared/dict/dictChoices'
import { provideBusy } from '@/shared/busy'
import { dictManager } from '@/shared/dict/DictManager'
import { useLoad } from '@/shared/loadable'
import DesktopFrame from '../frame/DesktopFrame.vue'
import { useSidePanel } from '../frame/useSidePanel'
import { tripOptionItem, tripOptionOf } from '../trips/tripOptionItem'
import TripOptionView from '../trips/TripOptionView.vue'
import WagonPanel from './WagonPanel.vue'
import WagonNewDialog from './WagonNewDialog.vue'
import { wagonsColumns } from './wagonsColumns'

/** Список вагонов: фильтры и таблица. */

const filter = reactive<WagonFilter>({ number: '', station: null, trip: null, locationKinds: [], states: [] })
// поиск по номеру: фильтры скрыты, сервер их не применяет (Ф-ВАГ-04)
const searching = computed(() => filter.number.trim() !== '')

const rows = shallowRef<WagonRow[]>([])
// общее ожидание страницы: спиннер на таблице — на всю цепочку загрузок (справочники, фильтры, список)
const busy = provideBusy()
const selectedId = ref<RowKey | null>(null)
// правая панель: рядом со списком на широком окне, поверх него — на узком
const panel = useSidePanel(selectedId)
const sort = ref<TableSort | null>(null)
const selected = computed(() => rows.value.find((row) => row.id === selectedId.value) ?? null)
// фильтр «Рейс»: все рейсы, у каждого — его маршрут (остановки, длина и вид маршрута в строке рейса)
const trips = shallowRef<TripOption[]>([])
const tripChoices = computed(() => trips.value.map((trip) => tripOptionItem(trip)))

// ошибка загрузки показывается на месте строк таблицы
const loadError = ref<ShownError | null>(null)

async function load() {
  loadError.value = null
  try {
    rows.value = await busy.run(fetchWagons(filter, sort.value))
  } catch (e) {
    rows.value = []
    loadError.value = shownError(e)
  }
}

// фильтр или сортировка изменились — данные загружаются заново
watch([filter, sort], load, { deep: true })

// новый вагон введён — список загружается заново, вагон становится выбранным
async function addWagon() {
  const wagon = await openDialog<WagonRow>(WagonNewDialog)
  if (!wagon) return
  await load()
  selectedId.value = wagon.id
}

// рейсы и маршруты — варианты фильтра «Рейс»: список вагонов их не ждёт; ошибка — в выпадающем списке «Рейс»
// варианты «Рейса»: рейсы и станции для строк вариантов (остановки) — параллельно, всё — загрузка выпадающего списка
const tripLoad = useLoad(async () => {
  const [, list] = await Promise.all([
    dictManager.prepareDict('Station'),
    fetchTripOptions({
      route: null,
      station: null,
      from: null,
      to: null,
      states: [TripStatus.PLANNED, TripStatus.IN_PROGRESS],
    }),
  ])
  trips.value = list
})

// цепочка загрузок — сразу в setup: спиннер виден с первой отрисовки; она же — повтор по клику на ошибку в таблице
async function init() {
  // справочники для строк (место, депо, тип и модель) и сами строки — независимо, параллельно
  await Promise.all([loadDicts(), load()])
}

async function loadDicts() {
  try {
    await busy.run(dictManager.prepareDicts('Station', 'Yard', 'WagonType', 'WagonModel', 'Depot'))
  } catch (e) {
    loadError.value = shownError(e)
  }
}
void init()
// варианты фильтра «Рейс» — своя загрузка, список её не ждёт; ошибка — в выпадающем списке «Рейс»
void tripLoad.run()
</script>

<style scoped lang="scss">
.wagons__search-note {
  color: $zs-text-muted;
  font-size: 0.8rem;
}
</style>
