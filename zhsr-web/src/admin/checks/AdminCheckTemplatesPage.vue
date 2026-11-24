<template>
  <DesktopFrame>
    <template #header>
      <h1 class="desktop-frame__title">{{ t('Шаблоны чек-листов') }}</h1>
      <div class="desktop-frame__actions">
        <q-btn
          unelevated
          outline
          no-caps
          color="ink"
          class="zs-soft-border"
          :disable="!selected"
          :title="selected ? undefined : t('Выберите шаблон чек-листа в списке')"
          @click="selected && openItems(selected)"
        >
          {{ t('Пункты') }}
        </q-btn>
        <q-btn unelevated no-caps color="primary" @click="edit(null)">{{ t('Создать шаблон') }}</q-btn>
      </div>
    </template>

    <DataTable
      v-model:selected="selectedId"
      deselect
      :columns="columns"
      :rows="rows"
      row-key="id"
      :sort="orderSort('sortOrder')"
      :struck="(r: CheckTemplate) => r.disabled"
      :loading="busy.active.value"
      :error="loadError"
      :retry="load"
      :row-menu="rowMenu"
      menu-gap
      empty="Шаблонов чек-листов нет"
      @row-dblclick="openItems"
    />
  </DesktopFrame>
</template>

<script setup lang="ts">
import { computed, ref, shallowRef } from 'vue'
import { useRouter } from 'vue-router'

import type { CheckTemplate } from '@/api/checks'
import { deleteCheckTemplate, fetchAdminCheckTemplates, saveCheckTemplate } from '@/api/checks'
import { t } from '@/boot/i18n'
import DesktopFrame from '@/desktop/frame/DesktopFrame.vue'
import { moveSortOrder } from '@/api/admin'
import { provideBusy } from '@/shared/busy'
import { CHECK_OBJECTS, checkObjectText } from '@/shared/checks/labels'
import { confirmDelete } from '@/shared/components/dialog/confirmDelete'
import DataTable from '@/shared/components/table/DataTable.vue'
import type { Column, RowMenuItem } from '@/shared/components/table/types'
import type { ShownError } from '@/shared/errors'
import { shownError } from '@/shared/errors'
import { nextOrder, openSpecDialog, ORDER_FIELDS, TEXT_FIELDS, textValues } from '../shared/spec'
import { canMoveIn, orderSort, provideSortOrder } from '../shared/sortOrder'
import { textColumns } from './textColumns'
import { useKeptSelection } from '@/desktop/frame/keptSelection'

/**
 * Шаблоны чек-листов (Ф-ЧЕК-02): название, объект проверки, порядок. «Пункты» и двойной клик открывают пункты и
 * нормативы шаблона; «+ Шаблон» заводит шаблон. Отключённый шаблон зачёркнут.
 */

const router = useRouter()
const busy = provideBusy()
const rows = shallowRef<CheckTemplate[]>([])
// выбранная строка остаётся при возврате на страницу
const selectedId = useKeptSelection()
const loadError = ref<ShownError | null>(null)
const selected = computed(() => rows.value.find((r) => r.id === selectedId.value) ?? null)

const columns: Column<CheckTemplate>[] = [
  ...textColumns<CheckTemplate>().slice(0, 2),
  { field: 'checkObject', title: 'Объект проверки', width: '10rem', format: (v) => checkObjectText(v as number) },
  ...textColumns<CheckTemplate>().slice(2),
]

// перестановка строк кнопками в колонке «Порядок»: список всегда в порядке показа
provideSortOrder<CheckTemplate>({
  reason: () => '',
  canMove: (row, up) => canMoveIn(rows.value, row, up),
  move: async (row, up) => {
    await moveSortOrder('check_list_template', row.id, up)
    await load()
    selectedId.value = row.id
  },
})

async function load() {
  loadError.value = null
  try {
    rows.value = await busy.run(fetchAdminCheckTemplates())
  } catch (e) {
    rows.value = []
    loadError.value = shownError(e)
  }
}
void load()

const rowMenu: RowMenuItem<CheckTemplate>[] = [
  { label: 'Пункты', run: (row) => openItems(row) },
  { label: 'Изменить', run: (row) => void edit(row) },
  { label: 'Удалить', run: (row) => void remove(row) },
  { label: 'Создать шаблон', separator: true, run: () => void edit(null) },
]

function openItems(tpl: CheckTemplate) {
  void router.push({ name: 'admin-check-template', params: { id: tpl.id } })
}

async function edit(tpl: CheckTemplate | null) {
  const result = await openSpecDialog<CheckTemplate>({
    title: tpl ? 'Шаблон чек-листа' : 'Новый шаблон чек-листа',
    okText: tpl ? 'Сохранить' : 'Создать',
    fields: [
      ...TEXT_FIELDS,
      { name: 'checkObject', label: 'Объект проверки', kind: 'single', required: true, choices: CHECK_OBJECTS },
      ...ORDER_FIELDS,
    ],
    initial: { ...textValues(tpl, nextOrder(rows.value)), checkObject: tpl?.checkObject ?? null },
    save: (v) => saveCheckTemplate(tpl?.id ?? null, v as never),
    remove: tpl ? () => deleteCheckTemplate(tpl.id) : null,
  })
  if (result === undefined) return
  await load()
  selectedId.value = result === 'deleted' ? null : result.id
}

async function remove(row: CheckTemplate) {
  if (!(await confirmDelete('Удаление записи', 'Удалить запись «{name}»?', { name: row.textRu }, () => deleteCheckTemplate(row.id)))) return
  await load()
  selectedId.value = null
}
</script>
