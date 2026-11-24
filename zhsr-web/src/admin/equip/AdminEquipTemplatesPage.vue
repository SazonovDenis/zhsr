<template>
  <DesktopFrame>
    <template #header>
      <h1 class="desktop-frame__title">{{ t('Шаблоны описей экипировки') }}</h1>
      <div class="desktop-frame__actions">
        <q-btn
          unelevated
          outline
          no-caps
          color="ink"
          class="zs-soft-border"
          :disable="!selected"
          :title="selected ? undefined : t('Выберите шаблон описи в списке')"
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
      :struck="(r: EquipTemplate) => r.disabled"
      :loading="busy.active.value"
      :error="loadError"
      :retry="load"
      :row-menu="rowMenu"
      menu-gap
      empty="Шаблонов описей нет"
      @row-dblclick="openItems"
    />
  </DesktopFrame>
</template>

<script setup lang="ts">
import { computed, ref, shallowRef } from 'vue'
import { useRouter } from 'vue-router'

import type { EquipTemplate } from '@/api/equip'
import { deleteEquipTemplate, fetchAdminEquipTemplates, saveEquipTemplate } from '@/api/equip'
import { t } from '@/boot/i18n'
import DesktopFrame from '@/desktop/frame/DesktopFrame.vue'
import { moveSortOrder } from '@/api/admin'
import { provideBusy } from '@/shared/busy'
import { confirmDelete } from '@/shared/components/dialog/confirmDelete'
import DataTable from '@/shared/components/table/DataTable.vue'
import type { Column, RowMenuItem } from '@/shared/components/table/types'
import { dictManager } from '@/shared/dict/DictManager'
import type { ShownError } from '@/shared/errors'
import { shownError } from '@/shared/errors'
import { canMoveIn, orderSort, provideSortOrder } from '../shared/sortOrder'
import { textColumns } from '../checks/textColumns'
import { nextOrder, openSpecDialog, ORDER_FIELDS, TEXT_FIELDS, textValues } from '../shared/spec'
import { useKeptSelection } from '@/desktop/frame/keptSelection'

/**
 * Шаблоны описей экипировки (Ф-ЭКП-01): название, неисправность заявки по недостаче, порядок. «Пункты» и двойной клик
 * открывают позиции и нормативы шаблона. Отключённый шаблон зачёркнут.
 */

const router = useRouter()
const busy = provideBusy()
const rows = shallowRef<EquipTemplate[]>([])
// выбранная строка остаётся при возврате на страницу
const selectedId = useKeptSelection()
const loadError = ref<ShownError | null>(null)
const selected = computed(() => rows.value.find((r) => r.id === selectedId.value) ?? null)

const columns: Column<EquipTemplate>[] = [
  ...textColumns<EquipTemplate>().slice(0, 2),
  {
    field: 'faultType',
    title: 'Заявка по недостаче',
    width: 'minmax(12rem, 1fr)',
    format: (v) => (v === null ? t('Без заявки') : dictManager.getValue('FaultType', v as number)),
  },
  ...textColumns<EquipTemplate>().slice(2),
]

// справочник неисправностей для строк и сами строки — параллельно
// перестановка строк кнопками в колонке «Порядок»: список всегда в порядке показа
provideSortOrder<EquipTemplate>({
  reason: () => '',
  canMove: (row, up) => canMoveIn(rows.value, row, up),
  move: async (row, up) => {
    await moveSortOrder('equip_list_template', row.id, up)
    await load()
    selectedId.value = row.id
  },
})

async function load() {
  loadError.value = null
  try {
    const [list] = await busy.run(Promise.all([fetchAdminEquipTemplates(), dictManager.prepareDict('FaultType')]))
    rows.value = list
  } catch (e) {
    rows.value = []
    loadError.value = shownError(e)
  }
}
void load()

const rowMenu: RowMenuItem<EquipTemplate>[] = [
  { label: 'Пункты', run: (row) => openItems(row) },
  { label: 'Изменить', run: (row) => void edit(row) },
  { label: 'Удалить', run: (row) => void remove(row) },
  { label: 'Создать шаблон', separator: true, run: () => void edit(null) },
]

function openItems(tpl: EquipTemplate) {
  void router.push({ name: 'admin-equip-template', params: { id: tpl.id } })
}

async function edit(tpl: EquipTemplate | null) {
  const result = await openSpecDialog<EquipTemplate>({
    title: tpl ? 'Шаблон описи' : 'Новый шаблон описи',
    okText: tpl ? 'Сохранить' : 'Создать',
    fields: [
      ...TEXT_FIELDS,
      {
        name: 'faultType',
        label: 'Неисправность заявки по недостаче',
        kind: 'fault',
        wide: true,
        placeholder: 'Без заявки',
      },
      ...ORDER_FIELDS,
    ],
    initial: { ...textValues(tpl, nextOrder(rows.value)), faultType: tpl?.faultType ?? null },
    save: (v) => saveEquipTemplate(tpl?.id ?? null, v as never),
    remove: tpl ? () => deleteEquipTemplate(tpl.id) : null,
  })
  if (result === undefined) return
  await load()
  selectedId.value = result === 'deleted' ? null : result.id
}

async function remove(row: EquipTemplate) {
  if (!(await confirmDelete('Удаление записи', 'Удалить запись «{name}»?', { name: row.textRu }, () => deleteEquipTemplate(row.id)))) return
  await load()
  selectedId.value = null
}
</script>
