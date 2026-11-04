<template>
  <DesktopFrame>
    <template #header>
      <h1 class="desktop-frame__title">{{ t(def?.title ?? '') }}</h1>
      <div class="desktop-frame__actions">
        <button type="button" class="zs-btn-primary" :disabled="!def" @click="edit(null)">{{ t('Добавить') }}</button>
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
import { fetchAdminRows } from '@/api/admin'
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
import { adminColumns, adminTable, forgetAdminOptions } from './adminTables'

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
