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
      <!-- поиск отменяет список и фильтры: они скрыты и не применяются, но не очищаются -->
      <span v-if="searching" class="requests__search-note">
        {{ t('При поиске список и фильтры не применяются.') }}
      </span>
      <template v-else>
        <CbSegment v-model="filter.scope" single required :values="scopes" />
        <PeriodPicker v-model:start-date="filter.from" v-model:end-date="filter.to" label="Период:" clearable />
      </template>
    </FilterBar>

    <FilterBar>
      <template v-if="!searching">
        <CbButton
          v-model="filter.statuses"
          label="Статус"
          :values="statusChoices"
          :hidden="[RequestStatus.CLOSED]"
          :max-button-count="6"
        />
        <CbButton v-model="filter.severities" label="Критичность" :values="severityChoices" :max-button-count="4" />
        <CbList v-model="filter.services" placeholder="Служба" :values="serviceChoices" style="width: 10rem" />
      </template>
      <template #right>{{ t('{n} заявок', rows.length) }}</template>
    </FilterBar>

    <DataTable
      v-model:selected="selectedId"
      :columns="requestsColumns"
      :rows="rows"
      :error="loadError"
      :retry="init"
      row-key="id"
      :sort="sort"
      :loading="busy.active.value"
      empty="Заявок по выбранному фильтру нет"
      @sort="sort = $event"
    />

    <template #right>
      <RequestPanel :request-id="selectedId === null ? null : Number(selectedId)" @close="panel.close" />
    </template>
  </DesktopFrame>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, shallowRef, watch } from 'vue'

import type { RepairRequest, RequestCounts } from '@/api/requests'
import { fetchRequestCounts, fetchRequests, requestFilter } from '@/api/requests'
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
import type { ShownError } from '@/shared/errors'
import { shownError } from '@/shared/errors'
import type { RowKey, TableSort } from '@/shared/components/table/types'
import { RequestStatus, ServiceType } from '@/shared/constants/systemDicts'
import { dictChoices } from '@/shared/dict/dictChoices'
import { provideBusy } from '@/shared/busy'
import { dictManager } from '@/shared/dict/DictManager'
import { loadCurrentUser } from '@/shared/user'
import DesktopFrame from '../frame/DesktopFrame.vue'
import { useSidePanel } from '../frame/useSidePanel'
import RequestDialog from './RequestDialog.vue'
import RequestPanel from './RequestPanel.vue'
import { requestsColumns } from './requestsColumns'

/**
 * Список заявок на ремонт (Ф-ЗАЯ-06): «созданные мной», «мне на исполнение», «все»; фильтры по периоду подачи, статусу, критичности,
 * службе, поиск по номеру, вагону и неисправности. Справа — карточка выбранной заявки с действиями.
 */

// по умолчанию — заявки, которые ждут действия пользователя; статусы не выбраны
const filter = reactive(requestFilter('todo'))
// поиск: список и фильтры скрыты, сервер их не применяет (Ф-ЗАЯ-06)
const searching = computed(() => filter.query.trim() !== '')

const scopes: ChoiceItem[] = [
  { id: 'mine', text: 'Созданные мной' },
  { id: 'todo', text: 'Мне на исполнение' },
  { id: 'all', text: 'Все' },
]

const rows = shallowRef<RepairRequest[]>([])
const counts = shallowRef<RequestCounts>({ requestStatusType: {}, severityType: {} })
// общее ожидание страницы: спиннер на таблице — на всю цепочку загрузок (справочники, фильтры, список)
const busy = provideBusy()
const selectedId = ref<RowKey | null>(null)
// правая панель: рядом со списком на широком окне, поверх него — на узком
const panel = useSidePanel(selectedId)
const sort = ref<TableSort | null>(null)

// у кнопок статуса и критичности — сколько заявок с этим значением по остальным фильтрам
const statusChoices = computed(() =>
  dictChoices('RequestStatus').map((c) => ({ ...c, count: counts.value.requestStatusType[Number(c.id)] ?? 0 })),
)
const severityChoices = computed(() =>
  dictChoices('Severity').map((c) => ({ ...c, count: counts.value.severityType[Number(c.id)] ?? 0 })),
)
// службы заявок: технические, клининг, экипировка
const serviceChoices = computed(() =>
  dictChoices('Service').filter((c) =>
    ([ServiceType.TECHNICAL, ServiceType.CLEANING, ServiceType.EQUIPMENT] as number[]).includes(Number(c.id)),
  ),
)

// ошибка загрузки показывается на месте строк таблицы
const loadError = ref<ShownError | null>(null)

async function load() {
  loadError.value = null
  void loadCounts()
  try {
    rows.value = await busy.run(fetchRequests(filter, sort.value))
  } catch (e) {
    rows.value = []
    loadError.value = shownError(e)
  }
}

// числа у кнопок фильтров — счётчики: ошибку их загрузки осознанно не показываем, кнопки остаются без чисел
async function loadCounts() {
  try {
    counts.value = await fetchRequestCounts(filter)
  } catch {
    counts.value = { requestStatusType: {}, severityType: {} }
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

// цепочка загрузок — сразу в setup: спиннер виден с первой отрисовки; она же — повтор по клику на ошибку в таблице
async function init() {
  // справочник неисправностей для строк и сами строки — параллельно; строки ждут только пользователя (заготовка API
  // заявок отбирает «мои» по нему)
  await Promise.all([loadDicts(), loadCurrentUser().then(load, (e: unknown) => (loadError.value = shownError(e)))])
}

async function loadDicts() {
  try {
    await busy.run(dictManager.prepareDict('FaultType'))
  } catch (e) {
    loadError.value = shownError(e)
  }
}
void init()
</script>

<style scoped lang="scss">
.requests__search-note {
  color: $zs-text-muted;
  font-size: 0.8rem;
}
</style>
