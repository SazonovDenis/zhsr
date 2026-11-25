<template>
  <DesktopFrame v-model:floating="panel.floating.value" :right-open="panel.open.value">
    <template #header>
      <h1 class="desktop-frame__title">{{ t('Заявки') }}</h1>
      <div class="desktop-frame__actions">
        <q-btn unelevated no-caps color="primary" @click="addRequest">{{ t('Новая заявка') }}</q-btn>
      </div>
    </template>

    <FilterBar>
      <TextInput
        ref="searchField"
        v-model="filter.query"
        placeholder="Номер, вагон, неисправность"
        width="15rem"
        @keydown.down.prevent="dataTable?.focus()"
        @keydown.esc="searchEsc($event, filter.query, () => (filter.query = ''))"
      />
      <!-- список: можно снять — тогда «все»; при фильтре по вагону недоступен -->
      <CbSegment v-model="list" single :values="listChoices" />
      <span v-if="wagonFilter" class="requests__chip q-py-xs q-pl-sm q-pr-xs">
        {{ t('Вагон № {number}', { number: wagonFilter.number }) }}
        <button type="button" class="requests__chip-clear q-pa-none" :title="t('Снять фильтр')" @click="clearWagon">
          ✕
        </button>
      </span>
      <!-- неисправности и депо исполнителя — переход из диаграммы «Заявки» -->
      <span v-if="filter.faultTypes.length" class="requests__chip q-py-xs q-pl-sm q-pr-xs" :title="faultTitle">
        {{ faultText }}
        <button
          type="button"
          class="requests__chip-clear q-pa-none"
          :title="t('Снять фильтр')"
          @click="filter.faultTypes = []"
        >
          ✕
        </button>
      </span>
      <span v-if="filter.depot !== null" class="requests__chip q-py-xs q-pl-sm q-pr-xs">
        {{ t('Депо исполнителя: {name}', { name: dictManager.getValue('Depot', filter.depot) }) }}
        <button
          type="button"
          class="requests__chip-clear q-pa-none"
          :title="t('Снять фильтр')"
          @click="filter.depot = null"
        >
          ✕
        </button>
      </span>
      <PeriodPicker v-model:start-date="filter.from" v-model:end-date="filter.to" label="Период:" clearable />
    </FilterBar>

    <FilterBar>
      <CbButton
        v-model="filter.statuses"
        label="Статус"
        :values="statusChoices"
        :hidden="[RequestStatus.CLOSED]"
        :max-button-count="6"
      />
      <CbButton v-model="filter.severities" label="Критичность" :values="severityChoices" :max-button-count="4" />
      <CbButton v-model="filter.services" label="Служба" :values="serviceChoices" />
      <template #right>{{ t('{n} заявок', rows.length) }}</template>
    </FilterBar>

    <DataTable
      ref="dataTable"
      v-model:selected="selectedId"
      :columns="requestsColumns"
      :rows="rows"
      :error="loadError"
      :retry="init"
      row-key="id"
      :sort="sort"
      :loading="busy.active.value"
      :row-menu="rowMenu"
      empty="Заявок по выбранному фильтру нет"
      @sort="sort = $event"
    />

    <template #right>
      <RequestPanel ref="panelRef" :request-id="selectedId === null ? null : Number(selectedId)" @close="panel.close" />
    </template>
  </DesktopFrame>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, reactive, ref, shallowRef, useTemplateRef, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import type { RepairRequest, RequestAction, RequestCounts } from '@/api/requests'
import { fetchRequestCounts, fetchRequests, requestFilter } from '@/api/requests'
import { t } from '@/boot/i18n'
import { bus } from '@/shared/bus'
import CbButton from '@/shared/components/choice/CbButton.vue'
import CbSegment from '@/shared/components/choice/CbSegment.vue'
import type { ChoiceItem } from '@/shared/components/choice/choice'
import { openDialog } from '@/shared/components/dialog/openDialog'
import FilterBar from '@/shared/components/FilterBar.vue'
import TextInput from '@/shared/components/form/TextInput.vue'
import { wagonRequestsPeriod } from '@/shared/requests/requestView'
import PeriodPicker from '@/shared/components/period/PeriodPicker.vue'
import DataTable from '@/shared/components/table/DataTable.vue'
import { useSearchFocus } from '@/shared/composables/searchFocus'
import { searchEsc, usePageSearchEsc } from '@/shared/composables/searchEsc'
import type { ShownError } from '@/shared/errors'
import { shownError } from '@/shared/errors'
import type { RowMenuItem, TableSort } from '@/shared/components/table/types'
import {
  ACTIONS,
  assignBlockReason,
  assignStatusReason,
  editBlockReason,
  requestActions,
} from '@/shared/requests/rights'
import { RequestStatus, ServiceType } from '@/shared/constants/systemDicts'
import { dictChoices } from '@/shared/dict/dictChoices'
import { provideBusy } from '@/shared/busy'
import { dictManager } from '@/shared/dict/DictManager'
import { currentUser, loadCurrentUser } from '@/shared/user'
import DesktopFrame from '../frame/DesktopFrame.vue'
import { useSidePanel } from '../frame/useSidePanel'
import RequestDialog from './RequestDialog.vue'
import RequestPanel from './RequestPanel.vue'
import { requestsColumns } from './requestsColumns'
import { useKeptSelection } from '../frame/keptSelection'

/**
 * Список заявок на ремонт (Ф-ЗАЯ-06): «созданные мной», «мне на исполнение», «все»; фильтры по периоду подачи, статусу, критичности,
 * службе, поиск по номеру, вагону и неисправности (складывается с остальными фильтрами). Справа — карточка выбранной заявки с действиями.
 */

const dataTable = useTemplateRef<{ focus: () => void }>('dataTable')
const searchField = useTemplateRef<InstanceType<typeof TextInput>>('searchField')
useSearchFocus(searchField)
const router = useRouter()
const route = useRoute()

// по умолчанию — заявки, которые ждут действия пользователя; статусы не выбраны
const filter = reactive(requestFilter('todo'))

/** Выбор списка: «созданные мной», «мне на исполнение», «действующие»; null — все. */
type ListChoice = 'mine' | 'todo' | 'open'
const list = ref<ListChoice | null>('todo')

/** Фильтр по вагону — переход «Заявки вагона» с панели вагона; отключает выбор списка. */
const wagonFilter = ref<{ id: number; number: string } | null>(null)

const LISTS: { id: ListChoice; text: string }[] = [
  { id: 'mine', text: 'Созданные мной' },
  { id: 'todo', text: 'Мне на исполнение' },
  { id: 'open', text: 'Действующие' },
]
const listChoices = computed<ChoiceItem[]>(() => LISTS.map((c) => ({ ...c, disabled: !!wagonFilter.value })))

// выбор списка и вагон → фильтр сервера: «действующие» — все видные, не в конечном статусе; без выбора и по вагону —
// все
watch(
  [list, wagonFilter],
  () => {
    const choice = wagonFilter.value ? null : list.value
    filter.scope = choice === 'mine' || choice === 'todo' ? choice : 'all'
    filter.closed = choice === 'open' ? false : null
    filter.wagon = wagonFilter.value?.id ?? null
  },
  { immediate: true },
)

/**
 * Переход «Заявки вагона» с панели вагона (?wagon=id&number=номер): все заявки вагона за месяц; остальные фильтры
 * и выбор списка сброшены, период можно менять.
 */
function applyRouteQuery() {
  // переход к заявке из проверки или описи (?request=id&number=номер): поиск по номеру, заявка выбрана
  const request = Number(route.query.request)
  if (request && typeof route.query.number === 'string') {
    Object.assign(filter, requestFilter('all', { query: route.query.number }))
    list.value = null
    wagonFilter.value = null
    selectedId.value = request
    void router.replace({ name: 'requests' })
    return
  }
  // переход из диаграммы «Заявки» (?service=id&faults=id,id&from=дата&to=дата&depot=id): те же заявки, что в
  // прямоугольнике диаграммы; остальные фильтры и выбор списка сброшены
  const { service, faults, from, to, depot } = route.query
  if (typeof faults === 'string' && typeof from === 'string' && typeof to === 'string') {
    const patch = {
      services: [Number(service)],
      faultTypes: faults.split(',').map(Number),
      from,
      to,
      statuses: [RequestStatus.DONE, RequestStatus.CLOSED],
      depot: Number(depot) || null,
    }
    Object.assign(filter, requestFilter('all', patch))
    list.value = null
    wagonFilter.value = null
    selectedId.value = null
    void router.replace({ name: 'requests' })
    return
  }
  const id = Number(route.query.wagon)
  const number = route.query.number
  if (!id || typeof number !== 'string') return
  Object.assign(filter, requestFilter('all', wagonRequestsPeriod()))
  list.value = null
  wagonFilter.value = { id, number }
  void router.replace({ name: 'requests' })
}

function clearWagon() {
  wagonFilter.value = null
}
// несколько неисправностей — прямоугольник «Прочие» диаграммы: в отметке их число, названия — в подсказке
const faultNames = computed(() => filter.faultTypes.map((id) => dictManager.getValue('FaultType', id)))
const faultText = computed(() =>
  faultNames.value.length === 1
    ? t('Неисправность: {name}', { name: faultNames.value[0]! })
    : t('Неисправностей: {n}', { n: faultNames.value.length }),
)
const faultTitle = computed(() => faultNames.value.join('\n'))

const rows = shallowRef<RepairRequest[]>([])
const counts = shallowRef<RequestCounts>({ requestStatusType: {}, severityType: {} })
// общее ожидание страницы: спиннер на таблице — на всю цепочку загрузок (справочники, фильтры, список)
const busy = provideBusy()
// выбранная строка остаётся при возврате на страницу
const selectedId = useKeptSelection()
usePageSearchEsc(
  () => filter.query,
  () => (filter.query = ''),
  selectedId,
)
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

applyRouteQuery()
watch(() => [route.query.wagon, route.query.request, route.query.faults], applyRouteQuery)

// фильтр или сортировка изменились — данные загружаются заново
watch([filter, sort], load, { deep: true })

// новая заявка подана — список загружается заново, заявка становится выбранной
// --- меню строки: те же действия и пояснения, что у кнопок панели заявки, затем действие со списком «Новая заявка»

const panelRef = ref<InstanceType<typeof RequestPanel> | null>(null)

/** Запустить действие панели по заявке строки: меню уже выбрало строку, панель открывается и грузит карточку. */
async function withPanel(row: RepairRequest, run: (p: InstanceType<typeof RequestPanel>) => unknown) {
  await nextTick()
  const p = panelRef.value
  if (p && (await p.ready(row.id))) await run(p)
}

/** Перевести причину из прав заявки: в тексте может быть статус заявки. */
function reasonText(row: RepairRequest, reason: string): string {
  return reason ? t(reason, { status: dictManager.getValue('RequestStatus', row.requestStatusType) }) : ''
}

const rowMenu: RowMenuItem<RepairRequest>[] = [
  ...(Object.keys(ACTIONS) as RequestAction[]).map((action) => ({
    label: ACTIONS[action].title,
    disabled: (row: RepairRequest) => {
      const user = currentUser.value
      if (!user) return ''
      return reasonText(row, requestActions(user, row).find((a) => a.action === action)?.reason ?? '')
    },
    run: (row: RepairRequest) => void withPanel(row, (p) => p.onAction(action)),
  })),
  {
    label: 'Исправить',
    disabled: (row) => (currentUser.value ? reasonText(row, editBlockReason(currentUser.value, row)) : ''),
    run: (row) => void withPanel(row, (p) => p.edit()),
  },
  // исполнитель уже есть — «Переназначить», иначе «Назначить»
  // виден один из двух пунктов (или ни одного), поэтому черта — над обоими
  ...[false, true].map((reassign) => ({
    label: reassign ? 'Переназначить исполнителя' : 'Назначить исполнителя',
    separator: true,
    // статус не подходит (выполнена, закрыта…) — пункта нет; нет прав — неактивен с причиной
    visible: (row: RepairRequest) => !!row.executorUsr === reassign && !assignStatusReason(row),
    disabled: (row: RepairRequest) => {
      const reason = currentUser.value ? assignBlockReason(currentUser.value, row) : ''
      const text = reassign ? 'Нельзя переназначить: {reason}' : 'Нельзя назначить: {reason}'
      return reason ? t(text, { reason: reasonText(row, reason) }) : ''
    },
    run: (row: RepairRequest) => void withPanel(row, (p) => p.assign()),
  })),
  { label: 'Добавить примечание', run: (row) => void withPanel(row, (p) => p.addNote()) },
  { label: 'Новая заявка', separator: true, run: () => void addRequest() },
]

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
    await busy.run(dictManager.prepareDicts('FaultType', 'WagonType', 'Depot'))
  } catch (e) {
    loadError.value = shownError(e)
  }
}
void init()
</script>

<style scoped lang="scss">
.requests__chip {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  border: 0.0625rem solid $primary;
  border-radius: 1rem;
  background: $zs-selected-bg;
  font-size: 0.8rem;
}

.requests__chip-clear {
  border: 0;
  background: none;
  color: $zs-text-muted;
  font: inherit;
  cursor: pointer;
}
</style>
