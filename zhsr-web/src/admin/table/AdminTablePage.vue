<template>
  <DesktopFrame>
    <template #header>
      <h1 class="desktop-frame__title">{{ t(def?.title ?? '') }}</h1>
      <div class="desktop-frame__actions">
        <q-btn unelevated no-caps color="primary" :disable="!def" @click="edit(null)">{{ t('Добавить') }}</q-btn>
      </div>
    </template>

    <FilterBar>
      <TextInput
        ref="searchField"
        v-model="list.query.search"
        width="18rem"
        placeholder="Поиск"
        @keydown.down.prevent="dataTable?.focus()"
        @keydown.esc="searchEsc($event, list.query.search, () => (list.query.search = ''))"
      />
      <template #right>
        <AdminPager v-model="list.query.page" :total="list.total.value" :size="list.query.size" />
      </template>
    </FilterBar>

    <!-- таблица с картой (ADMIN_MAPS) — карта справа от списка; у остальных обёртки в раскладке не участвуют -->
    <div :class="hasMap ? 'admin-table__split' : 'admin-table__plain'">
      <div :class="hasMap ? 'admin-table__list' : 'admin-table__plain'">
        <DataTable
          ref="dataTable"
          v-model:selected="selectedId"
          deselect
          :columns="columns"
          :rows="list.rows.value"
          row-key="id"
          :sort="list.query.sort"
          :struck="isDisabled"
          :loading="busy.active.value"
          :error="loadError || list.error.value"
          :retry="def ? list.load : () => open(table)"
          empty="Записей нет"
          :row-menu="rowMenu"
          @sort="list.query.sort = $event"
          @row-dblclick="edit"
        />
      </div>
      <AdminTableMap
        v-if="hasMap"
        ref="tableMap"
        :key="table"
        :table="table"
        :search="list.query.search"
        :row="selectedRow"
        class="admin-table__map"
        @pick="list.query.search = $event"
        @clear="clearPicked"
        @open="openRow"
      />
    </div>
  </DesktopFrame>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, shallowRef, useTemplateRef, watch } from 'vue'

import type { AdminRow, AdminTable } from '@/api/admin'
import { deleteAdminRow, fetchAdminRow, fetchAdminRows, moveSortOrder } from '@/api/admin'
import { t } from '@/boot/i18n'
import { provideBusy } from '@/shared/busy'
import { confirmDelete } from '@/shared/components/dialog/confirmDelete'
import { openDialog } from '@/shared/components/dialog/openDialog'
import FilterBar from '@/shared/components/FilterBar.vue'
import TextInput from '@/shared/components/form/TextInput.vue'
import DataTable from '@/shared/components/table/DataTable.vue'
import { useSearchFocus } from '@/shared/composables/searchFocus'
import { searchEsc } from '@/shared/composables/searchEsc'
import type { RowMenuItem } from '@/shared/components/table/types'
import type { ShownError } from '@/shared/errors'
import { notifyError, shownError } from '@/shared/errors'
import DesktopFrame from '@/desktop/frame/DesktopFrame.vue'
import AdminPager from '../shared/AdminPager.vue'
import { useAdminList } from '../shared/useAdminList'
import AdminTableMap from './AdminTableMap.vue'
import AdminRowDialog from './AdminRowDialog.vue'
import { sortFields } from '../shared/adminFieldOrder'
import { provideSortOrder } from '../shared/sortOrder'
import { ADMIN_MAPS } from './adminMaps'
import { adminColumns, adminTable, forgetAdminOptions, SORT_ORDER, stageName } from './adminTables'
import { useKeptSelection } from '@/desktop/frame/keptSelection'

/**
 * Общий редактор таблицы администрирования: поиск, страницы, сортировка по колонкам. «Добавить», двойной клик по
 * строке и «Редактировать» в меню строки открывают диалог записи, «Удалить» в меню — удаляет с подтверждением. Колонки и поля формы — из описания таблицы с сервера. Отключённые записи зачёркнуты.
 */

const props = defineProps<{ table: string }>()

const hasMap = computed(() => !!ADMIN_MAPS[props.table])
const dataTable = useTemplateRef<{ focus: () => void }>('dataTable')
const searchField = useTemplateRef<InstanceType<typeof TextInput>>('searchField')
useSearchFocus(searchField)
const tableMap = useTemplateRef('tableMap')

const def = shallowRef<AdminTable | null>(null)
// общее ожидание страницы: спиннер на таблице — на всю цепочку (описание таблицы, строки)
const busy = provideBusy()
// ошибка загрузки описания таблицы, переведённая; показывается на месте строк
const loadError = ref<ShownError | null>(null)
// выбранная строка остаётся при возврате на страницу
const selectedId = useKeptSelection()
const columns = computed(() => (def.value ? adminColumns(def.value) : []))

const list = useAdminList((query) => fetchAdminRows(props.table, query), busy)
const selectedRow = computed(() => list.rows.value.find((row) => row.id === selectedId.value) ?? null)

// перестановка строк кнопками в колонке «Порядок показа»: только при сортировке по нему (по умолчанию — по нему)
provideSortOrder<AdminRow>({
  reason: () => {
    const sort = list.query.sort
    return sort?.field === SORT_ORDER && !sort.desc
      ? ''
      : t('Переставлять строки можно при сортировке по порядку показа')
  },
  canMove: (row, up) => {
    const rows = list.rows.value
    const i = rows.indexOf(row)
    const lastPage = (list.query.page + 1) * list.query.size >= list.total.value
    return up ? !(list.query.page === 0 && i === 0) : !(lastPage && i === rows.length - 1)
  },
  move: async (row, up) => {
    await moveSortOrder(props.table, row.id, up)
    forgetAdminOptions(props.table)
    selectedId.value = row.id
    await list.load()
  },
})

// отключённая запись — зачёркнутая строка; поле disabled есть в строке, своей колонки у него нет
const isDisabled = (row: AdminRow) => row.disabled === true

// описание таблицы и строки; она же — повтор по клику на ошибку в таблице
async function open(name: string) {
  def.value = null
  loadError.value = null
  list.query.search = ''
  list.query.sort = null
  list.query.page = 0
  try {
    def.value = await busy.run(adminTable(name))
    if (!def.value) {
      loadError.value = { text: t('Таблица не редактируется в администрировании'), request: '' }
      return
    }
    // таблица с порядком показа по умолчанию отсортирована по нему — значок сортировки на колонке «Порядок»
    if (def.value.columns.some((c) => c.name === SORT_ORDER)) {
      list.query.sort = { field: SORT_ORDER, desc: false }
      return
    }
    // остальные — по первой колонке (название): сортировка сразу видна на ней
    const first = columns.value[0]
    if (first?.sortable) list.query.sort = { field: first.sortField ?? first.field, desc: false }
    else await list.load()
  } catch (e) {
    loadError.value = shownError(e)
  }
}

watch(() => props.table, open, { immediate: true })

const rowMenu: RowMenuItem<AdminRow>[] = [
  { label: 'Редактировать', run: (row) => void edit(row) },
  { label: 'Удалить', run: (row) => void remove(row) },
]

// Escape, когда строка не выбрана, очищает поиск. На window — после таблицы (document): выбор она снимает сама и
// отмечает событие обработанным
function onEscape(e: KeyboardEvent) {
  if (e.key !== 'Escape' || e.defaultPrevented || !list.query.search) return
  // открыто меню или диалог — Escape их
  if (document.querySelector('.q-dialog, .q-menu, .row-menu')) return
  list.query.search = ''
}

window.addEventListener('keydown', onEscape)
onBeforeUnmount(() => window.removeEventListener('keydown', onEscape))

/** Повторный щелчок по точке на карте: снять выбор и очистить поиск. */
function clearPicked() {
  selectedId.value = null
  list.query.search = ''
}

/** Открыть запись по id (двойной щелчок по записи на карте): её может не быть на странице списка. */
async function openRow(id: number) {
  try {
    await edit(await busy.run(fetchAdminRow(props.table, id)))
  } catch (e) {
    notifyError(e)
  }
}

/** Название записи: у перегона — станции, у остальных — первое поле формы. */
function rowName(table: AdminTable, row: AdminRow): string {
  if (table.name === 'stage') return stageName(row)
  const col = sortFields(table.name, table.columns, (c) => c.name).find((c) => c.name !== SORT_ORDER)
  const value = col && row[col.type === 'REF' ? `${col.name}_text` : col.name]
  return typeof value === 'string' || typeof value === 'number' ? String(value) : ''
}

async function remove(row: AdminRow) {
  const table = def.value
  if (!table) return
  const name = rowName(table, row)
  if (
    !(await confirmDelete('Удаление записи', 'Удалить запись «{name}»?', { name }, () =>
      deleteAdminRow(table.name, row.id),
    ))
  )
    return
  forgetAdminOptions(table.name)
  tableMap.value?.reload()
  selectedId.value = null
  await list.load()
}

async function edit(row: AdminRow | null) {
  if (!def.value) return
  const result = await openDialog<AdminRow | 'deleted'>(AdminRowDialog, { table: def.value, row })
  if (!result) return
  // запись могла быть вариантом поля-ссылки другой таблицы: варианты загрузятся заново
  forgetAdminOptions(def.value.name)
  tableMap.value?.reload()
  selectedId.value = result === 'deleted' ? null : result.id
  await list.load()
}
</script>

<style scoped lang="scss">
.admin-table__plain {
  display: contents;
}

.admin-table__split {
  display: flex;
  flex: 1 1 0;
  gap: 1rem;
  min-height: 0;
}

.admin-table__list {
  display: flex;
  flex: 1 1 0;
  flex-direction: column;
  min-width: 0;
}

.admin-table__map {
  flex: 1 1 0;
}
</style>
