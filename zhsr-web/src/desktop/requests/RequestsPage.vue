<template>
  <DesktopFrame v-model:floating="panel.floating.value" :right-open="panel.open.value" right-width="25rem">
    <template #header>
      <h1 class="desktop-frame__title">{{ t('Заявки') }}</h1>
      <div class="desktop-frame__actions">
        <button type="button" class="zs-btn-primary" @click="addRequest">{{ t('Новая заявка') }}</button>
      </div>
    </template>

    <FilterBar>
      <TextInput v-model="filter.query" placeholder="Номер, вагон, неисправность" width="15rem" />
      <CbSegment v-model="filter.scope" single required :values="scopes" />
      <PeriodPicker v-model:start-date="filter.from" v-model:end-date="filter.to" label="Период:" clearable />
    </FilterBar>

    <FilterBar>
      <CbButton v-model="filter.statuses" label="Статус" :values="statusChoices" :max-button-count="5" />
      <CbButton v-model="filter.severities" label="Критичность" :values="severityChoices" :max-button-count="4" />
      <CbList v-model="filter.services" placeholder="Служба" :values="serviceChoices" style="width: 10rem" />
      <CbList v-model="filter.trip" single placeholder="Рейс" :values="tripChoices" style="width: 15rem">
        <template #option="{ item }">
          <TripOption :trip="tripOf(item)" class="trip-option--list" />
        </template>
      </CbList>
      <template #right>{{ t('{n} заявок', rows.length) }}</template>
    </FilterBar>

    <DataTable
      v-model:selected="selectedId"
      :columns="requestsColumns"
      :rows="rows"
      row-key="id"
      :sort="sort"
      :loading="loading"
      empty="Заявок по выбранному фильтру нет"
      @sort="sort = $event"
    />

    <template #right>
      <RequestPanel :request-id="selectedId === null ? null : String(selectedId)" @close="panel.close" />
    </template>
  </DesktopFrame>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, shallowRef, watch } from 'vue'

import type { RepairRequest, RequestCounts } from '@/api/requests'
import { fetchRequestCounts, fetchRequests, requestFilter } from '@/api/requests'
import type { Trip } from '@/api/trips'
import { fetchTrips } from '@/api/trips'
import { t } from '@/boot/i18n'
import { bus } from '@/shared/bus'
import CbButton from '@/shared/components/choice/CbButton.vue'
import CbList from '@/shared/components/choice/CbList.vue'
import CbSegment from '@/shared/components/choice/CbSegment.vue'
import type { ChoiceItem } from '@/shared/components/choice/choice'
import { openDialog } from '@/shared/components/dialog/openDialog'
import FilterBar from '@/shared/components/FilterBar.vue'
import TextInput from '@/shared/components/form/TextInput.vue'
import PeriodPicker from '@/shared/components/period/PeriodPicker.vue'
import DataTable from '@/shared/components/table/DataTable.vue'
import type { RowKey, TableSort } from '@/shared/components/table/types'
import { dictChoices } from '@/shared/dict/dictChoices'
import { dictManager } from '@/shared/dict/DictManager'
import { REQUEST_DICTS } from '@/shared/requests/requestView'
import { loadCurrentUser } from '@/shared/user'
import DesktopFrame from '../frame/DesktopFrame.vue'
import { useSidePanel } from '../frame/useSidePanel'
import { tripChoiceItem, tripOf } from '../trips/tripChoice'
import TripOption from '../trips/TripOption.vue'
import RequestDialog from './RequestDialog.vue'
import RequestPanel from './RequestPanel.vue'
import { requestsColumns } from './requestsColumns'

/**
 * Список заявок на ремонт (Ф-ЗАЯ-06): «мои», «на исполнение», «все»; фильтры по периоду подачи, статусу, критичности,
 * службе и рейсу, поиск по номеру, вагону и неисправности. Справа — карточка выбранной заявки с действиями.
 */

const filter = reactive(requestFilter('all'))

const scopes: ChoiceItem[] = [
  { id: 'mine', text: 'Мои' },
  { id: 'todo', text: 'На исполнение' },
  { id: 'all', text: 'Все' },
]

const rows = shallowRef<RepairRequest[]>([])
const counts = shallowRef<RequestCounts>({ statuses: {}, severities: {} })
const loading = ref(false)
const selectedId = ref<RowKey | null>(null)
// правая панель: рядом со списком на широком окне, поверх него — на узком
const panel = useSidePanel(selectedId)
const sort = ref<TableSort | null>(null)

// у кнопок статуса и критичности — сколько заявок с этим значением по остальным фильтрам
const statusChoices = computed(() =>
  dictChoices('RequestStatus').map((c) => ({ ...c, count: counts.value.statuses[c.id] ?? 0 })),
)
const severityChoices = computed(() =>
  dictChoices('Severity').map((c) => ({ ...c, count: counts.value.severities[c.id] ?? 0 })),
)
// службы заявок: технические, клининг, экипировка
const serviceChoices = computed(() =>
  dictChoices('Service').filter((c) => ['TECHNICAL', 'CLEANING', 'EQUIPMENT'].includes(String(c.id))),
)

const trips = shallowRef<Trip[]>([])
const tripChoices = computed(() => trips.value.map((trip) => tripChoiceItem(trip)))

async function load() {
  loading.value = true
  try {
    ;[rows.value, counts.value] = await Promise.all([fetchRequests(filter, sort.value), fetchRequestCounts(filter)])
  } finally {
    loading.value = false
  }
}

// фильтр или сортировка изменились — данные загружаются заново
watch([filter, sort], load, { deep: true })

// новая заявка подана — список загружается заново, заявка становится выбранной
async function addRequest() {
  const request = await openDialog<RepairRequest>(RequestDialog)
  if (!request) return
  bus.emit('state-refresh')
  await load()
  selectedId.value = request.id
}

const onRefresh = () => void load()
bus.on('requests-refresh', onRefresh)
onBeforeUnmount(() => bus.off('requests-refresh', onRefresh))

onMounted(async () => {
  await Promise.all([dictManager.prepareDicts(...REQUEST_DICTS, 'TripState'), loadCurrentUser()])
  trips.value = await fetchTrips({ route: null, station: null, from: null, to: null, states: [] }, null)
  await load()
})
</script>
