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
      <!-- поиск отменяет список и фильтры, кроме периода: они скрыты и не применяются, но не очищаются -->
      <span v-if="searching" class="requests__search-note">
        {{ t('При поиске список и фильтры, кроме периода, не применяются.') }}
      </span>
      <template v-else>
        <!-- список: можно снять — тогда «все»; при фильтре по вагону недоступен -->
        <CbSegment v-model="list" single :values="listChoices" />
        <span v-if="wagonFilter" class="requests__wagon q-py-xs q-pl-sm q-pr-xs">
          {{ t('Вагон № {number}', { number: wagonFilter.number }) }}
          <button type="button" class="requests__wagon-clear q-pa-none" :title="t('Снять фильтр')" @click="clearWagon">
            ✕
          </button>
        </span>
      </template>
      <!-- период доступен всегда, и при поиске -->
      <PeriodPicker v-model:start-date="filter.from" v-model:end-date="filter.to" label="Период:" clearable />
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
import { computed, nextTick, onBeforeUnmount, reactive, ref, shallowRef, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import type { RepairRequest, RequestAction, RequestCounts } from '@/api/requests'
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
import { wagonRequestsPeriod } from '@/shared/requests/requestView'
import PeriodPicker from '@/shared/components/period/PeriodPicker.vue'
import DataTable from '@/shared/components/table/DataTable.vue'
import type { ShownError } from '@/shared/errors'
import { shownError } from '@/shared/errors'
import type { RowKey, RowMenuItem, TableSort } from '@/shared/components/table/types'
import { ACTIONS, assignBlockReason, editBlockReason, requestActions } from '@/shared/requests/rights'
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

/**
 * Список заявок на ремонт (Ф-ЗАЯ-06): «созданные мной», «мне на исполнение», «все»; фильтры по периоду подачи, статусу, критичности,
 * службе, поиск по номеру, вагону и неисправности. Справа — карточка выбранной заявки с действиями.
 */

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
// поиск: список и фильтры скрыты, сервер их не применяет (Ф-ЗАЯ-06)
const searching = computed(() => filter.query.trim() !== '')

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

applyRouteQuery()
watch(() => route.query.wagon, applyRouteQuery)

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
  // виден всегда один из двух пунктов, поэтому черта — над обоими
  ...[false, true].map((reassign) => ({
    label: reassign ? 'Переназначить исполнителя' : 'Назначить исполнителя',
    separator: true,
    visible: (row: RepairRequest) => !!row.executorUsr === reassign,
    disabled: (row: RepairRequest) => {
      const reason = currentUser.value ? assignBlockReason(currentUser.value, row) : ''
      const text = reassign ? 'Вы не можете переназначить: {reason}' : 'Вы не можете назначить: {reason}'
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
    await busy.run(dictManager.prepareDict('FaultType'))
  } catch (e) {
    loadError.value = shownError(e)
  }
}
void init()
</script>

<style scoped lang="scss">
.requests__wagon {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  border: 0.0625rem solid $primary;
  border-radius: 1rem;
  background: $zs-selected-bg;
  font-size: 0.8rem;
}

.requests__wagon-clear {
  border: 0;
  background: none;
  color: $zs-text-muted;
  font: inherit;
  cursor: pointer;
}

.requests__search-note {
  color: $zs-text-muted;
  font-size: 0.8rem;
}
</style>
