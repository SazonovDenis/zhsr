<template>
  <DesktopFrame>
    <template #header>
      <h1 class="desktop-frame__title">{{ t(def?.title ?? '') }}</h1>
      <div class="desktop-frame__actions">
        <q-btn unelevated no-caps color="primary" :disable="!def" @click="edit(null)">{{ t('Добавить') }}</q-btn>
      </div>
    </template>

    <FilterBar>
      <TextInput v-model="list.query.search" width="18rem" placeholder="Поиск" />
      <template #right>
        <AdminPager v-model="list.query.page" :total="list.total.value" :size="list.query.size" />
      </template>
    </FilterBar>

    <DataTable
      v-model:selected="selectedId"
      :columns="columns"
      :rows="list.rows.value"
      row-key="id"
      :sort="list.query.sort"
      :struck="isDisabled"
      :loading="busy.active.value"
      :error="loadError || list.error.value"
      :retry="def ? list.load : () => open(table)"
      empty="Записей нет"
      @sort="list.query.sort = $event"
      @row-dblclick="edit"
    />
  </DesktopFrame>
</template>

<script setup lang="ts">
import { computed, ref, shallowRef, watch } from 'vue'

import type { AdminRow, AdminTable } from '@/api/admin'
import { fetchAdminRows, moveSortOrder } from '@/api/admin'
import { t } from '@/boot/i18n'
import { provideBusy } from '@/shared/busy'
import { openDialog } from '@/shared/components/dialog/openDialog'
import FilterBar from '@/shared/components/FilterBar.vue'
import TextInput from '@/shared/components/form/TextInput.vue'
import DataTable from '@/shared/components/table/DataTable.vue'
import type { ShownError } from '@/shared/errors'
import { shownError } from '@/shared/errors'
import type { RowKey } from '@/shared/components/table/types'
import DesktopFrame from '@/desktop/frame/DesktopFrame.vue'
import AdminPager from '../shared/AdminPager.vue'
import { useAdminList } from '../shared/useAdminList'
import AdminRowDialog from './AdminRowDialog.vue'
import { provideSortOrder } from '../shared/sortOrder'
import { adminColumns, adminTable, forgetAdminOptions, SORT_ORDER } from './adminTables'

/**
 * Общий редактор таблицы администрирования: поиск, страницы, сортировка по колонкам. «Добавить» и двойной клик по
 * строке открывают диалог записи. Колонки и поля формы — из описания таблицы с сервера. Отключённые записи зачёркнуты.
 */

const props = defineProps<{ table: string }>()

const def = shallowRef<AdminTable | null>(null)
// общее ожидание страницы: спиннер на таблице — на всю цепочку (описание таблицы, строки)
const busy = provideBusy()
// ошибка загрузки описания таблицы, переведённая; показывается на месте строк
const loadError = ref<ShownError | null>(null)
const selectedId = ref<RowKey | null>(null)
const columns = computed(() => (def.value ? adminColumns(def.value) : []))

const list = useAdminList((query) => fetchAdminRows(props.table, query), busy)

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
    await list.load()
  } catch (e) {
    loadError.value = shownError(e)
  }
}

watch(() => props.table, open, { immediate: true })

async function edit(row: AdminRow | null) {
  if (!def.value) return
  const result = await openDialog<AdminRow | 'deleted'>(AdminRowDialog, { table: def.value, row })
  if (!result) return
  // запись могла быть вариантом поля-ссылки другой таблицы: варианты загрузятся заново
  forgetAdminOptions(def.value.name)
  selectedId.value = result === 'deleted' ? null : result.id
  await list.load()
}
</script>
