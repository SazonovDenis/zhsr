<template>
  <DesktopFrame>
    <template #header>
      <h1 class="desktop-frame__title">{{ t('Пользователи') }}</h1>
      <div class="desktop-frame__actions">
        <button type="button" class="zs-btn-primary" @click="edit(null)">{{ t('Новый пользователь') }}</button>
      </div>
    </template>

    <FilterBar>
      <TextInput v-model="list.query.search" width="18rem" placeholder="Поиск: ФИО, логин, телефон" />
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
      :struck="(user: AdminUser) => user.disabled"
      :loading="busy.active.value"
      :error="loadError || list.error.value"
      :retry="loadError ? init : list.load"
      empty="Пользователей нет"
      @sort="list.query.sort = $event"
      @row-dblclick="edit"
    />
  </DesktopFrame>
</template>

<script setup lang="ts">
import { computed, ref, shallowRef } from 'vue'

import type { AdminUser } from '@/api/admin'
import { fetchAdminUsers } from '@/api/admin'
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
import { adminOptions } from '../table/adminTables'
import AdminUserDialog from './AdminUserDialog.vue'
import { usersColumns } from './usersColumns'

/**
 * Пользователи (сотрудники): поиск, страницы, сортировка по ФИО и логину. Таблица собирает колонки из сотрудника, его
 * ролей и депо; отключённый сотрудник зачёркнут. «Новый пользователь» и двойной клик по строке открывают карточку
 * сотрудника.
 */

// общее ожидание страницы: спиннер на таблице — на всю цепочку (роли и депо, список)
const busy = provideBusy()
const list = useAdminList(fetchAdminUsers, busy)
const selectedId = ref<RowKey | null>(null)
// ошибка загрузки ролей и депо, переведённая; показывается на месте строк
const loadError = ref<ShownError | null>(null)

const roles = shallowRef(new Map<number, string>())
const depots = shallowRef(new Map<number, string>())
const columns = computed(() => usersColumns({ roles: roles.value, depots: depots.value }))

// цепочка загрузок — сразу в setup: спиннер виден с первой отрисовки
async function init() {
  loadError.value = null
  try {
    const [roleList, depotList] = await busy.run(Promise.all([adminOptions('role_type'), adminOptions('depot')]))
    roles.value = new Map(roleList.map((o) => [o.id, o.text]))
    depots.value = new Map(depotList.map((o) => [o.id, o.text]))
  } catch (e) {
    loadError.value = shownError(e)
    return
  }
  await list.load()
}
void init()

async function edit(user: AdminUser | null) {
  const result = await openDialog<AdminUser>(AdminUserDialog, { user })
  if (!result) return
  selectedId.value = result.id
  await list.load()
}
</script>
