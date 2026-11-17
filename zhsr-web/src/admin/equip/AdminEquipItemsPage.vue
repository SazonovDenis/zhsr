<template>
  <DesktopFrame v-model:floating="panel.floating.value" :back="back" :right-open="panel.open.value">
    <template #header>
      <h1 class="desktop-frame__title">{{ title }}</h1>
      <div class="desktop-frame__actions">
        <q-btn
          unelevated
          outline
          no-caps
          class="zs-soft-border"
          color="ink"
          :disable="!template"
          @click="edit(null, true)"
        >
          {{ t('Добавить раздел') }}
        </q-btn>
        <q-btn unelevated no-caps color="primary" :disable="!template" @click="edit(null, false)">
          {{ t('Добавить позицию') }}
        </q-btn>
      </div>
    </template>

    <DataTable
      v-model:selected="selectedId"
      :columns="columns"
      :rows="rows"
      row-key="id"
      :sort="orderSort('sortOrder')"
      :struck="(r: ItemRow) => r.item.disabled"
      :loading="busy.active.value"
      :error="loadError"
      :retry="init"
      :row-menu="rowMenu"
      empty="В шаблоне нет позиций"
      @row-dblclick="(r: ItemRow) => void edit(r.item, r.section)"
    />

    <template #right>
      <EquipItemPanel :item="selectedItem" @close="panel.close" />
    </template>
  </DesktopFrame>
</template>

<script setup lang="ts">
import { computed, ref, shallowRef } from 'vue'
import { useRouter } from 'vue-router'

import type { EquipItem, EquipItemBody, EquipTemplate } from '@/api/equip'
import { deleteEquipItem, fetchAdminEquipTemplates, fetchEquipItems, saveEquipItem } from '@/api/equip'
import { t } from '@/boot/i18n'
import DesktopFrame from '@/desktop/frame/DesktopFrame.vue'
import { useSidePanel } from '@/desktop/frame/useSidePanel'
import { moveSortOrder } from '@/api/admin'
import { provideBusy } from '@/shared/busy'
import { treeRows } from '@/shared/checks/tree'
import DataTable from '@/shared/components/table/DataTable.vue'
import type { Column, RowKey, RowMenuItem } from '@/shared/components/table/types'
import type { ShownError } from '@/shared/errors'
import { shownError } from '@/shared/errors'
import CellTreeText from '../shared/CellTreeText.vue'
import { canMoveIn, orderSort, provideSortOrder, sortOrderColumn } from '../shared/sortOrder'
import { nextOrder, openSpecDialog, ORDER_FIELDS, TEXT_FIELDS, textValues } from '../shared/spec'
import EquipItemPanel from './EquipItemPanel.vue'

/**
 * Позиции шаблона описи экипировки (Ф-ЭКП-01): дерево разделов и позиций с единицей измерения. «+ Раздел», «+ Позиция»
 * и двойной клик открывают карточку; справа — нормативы выбранной позиции. Отключённая позиция зачёркнута.
 */
const props = defineProps<{ id: string }>()

interface ItemRow {
  id: number
  depth: number
  section: boolean
  text: string
  unit: string
  sortOrder: number
  item: EquipItem
}

const router = useRouter()
const busy = provideBusy()
const template = shallowRef<EquipTemplate | null>(null)
const items = shallowRef<EquipItem[]>([])
const selectedId = ref<RowKey | null>(null)
const panel = useSidePanel(selectedId)
const loadError = ref<ShownError | null>(null)
const templateId = computed(() => Number(props.id))

const title = computed(() =>
  template.value ? t('Позиции: {name}', { name: template.value.textRu }) : t('Позиции шаблона'),
)
const back = () => void router.push({ name: 'admin-equip-templates' })

const rows = computed<ItemRow[]>(() =>
  treeRows(items.value).map(({ item, depth }) => ({
    id: item.id,
    depth,
    sortOrder: item.sortOrder,
    section: item.unit === null,
    text: item.textRu,
    unit: item.unit ?? '',
    item,
  })),
)
const selectedItem = computed(() => items.value.find((i) => i.id === selectedId.value) ?? null)

const columns: Column<ItemRow>[] = [
  sortOrderColumn<ItemRow>('sortOrder'),
  { field: 'text', title: 'Наименование', width: 'minmax(20rem, 3fr)', cell: CellTreeText },
  { field: 'unit', title: 'Единица', width: '8rem' },
]

// перестановка пунктов кнопками в колонке «Порядок»: среди пунктов своего раздела
provideSortOrder<ItemRow>({
  reason: () => '',
  canMove: (row, up) =>
    canMoveIn(
      rows.value.filter((r) => r.item.parentItem === row.item.parentItem).map((r) => r.id),
      row.id,
      up,
    ),
  move: async (row, up) => {
    await moveSortOrder('equip_list_template_item', row.id, up)
    await loadItems()
    selectedId.value = row.id
  },
})

async function loadItems() {
  items.value = await busy.run(fetchEquipItems(templateId.value))
}

async function init() {
  loadError.value = null
  try {
    const [templates] = await busy.run(Promise.all([fetchAdminEquipTemplates(), loadItems()]))
    template.value = templates.find((x) => x.id === templateId.value) ?? null
  } catch (e) {
    loadError.value = shownError(e)
  }
}
void init()

const rowMenu: RowMenuItem<ItemRow>[] = [
  { label: 'Изменить', run: (row) => void edit(row.item, row.section) },
  { label: 'Добавить позицию в раздел', visible: (row) => row.section, run: (row) => void edit(null, false, row.id) },
  { label: 'Добавить раздел', separator: true, run: () => void edit(null, true) },
  { label: 'Добавить позицию', run: () => void edit(null, false) },
]

function sectionChoices(item: EquipItem | null) {
  const banned = new Set<number>()
  if (item) {
    banned.add(item.id)
    let grew = true
    while (grew) {
      grew = false
      for (const i of items.value) {
        if (i.parentItem !== null && banned.has(i.parentItem) && !banned.has(i.id)) {
          banned.add(i.id)
          grew = true
        }
      }
    }
  }
  return treeRows(items.value)
    .filter(({ item: i }) => i.unit === null && !banned.has(i.id))
    .map(({ item: i, depth }) => ({ id: i.id, text: `${'— '.repeat(depth)}${i.textRu}` }))
}

async function edit(item: EquipItem | null, section: boolean, parent: number | null = null) {
  const siblings = items.value.filter((i) => i.parentItem === (item?.parentItem ?? parent))
  const result = await openSpecDialog<EquipItem>({
    title: item ? (section ? 'Раздел' : 'Позиция') : section ? 'Новый раздел' : 'Новая позиция',
    okText: item ? 'Сохранить' : 'Добавить',
    fields: [
      ...TEXT_FIELDS,
      {
        name: 'parentItem',
        label: 'Раздел',
        kind: 'single',
        wide: true,
        placeholder: 'Верхний уровень',
        choices: sectionChoices(item),
      },
      ...(section
        ? []
        : [
            {
              name: 'unit',
              label: 'Единица измерения',
              kind: 'text' as const,
              required: true,
              placeholder: 'шт, г, комплект',
            },
          ]),
      ...ORDER_FIELDS,
    ],
    initial: {
      ...textValues(item, nextOrder(siblings)),
      parentItem: item?.parentItem ?? parent,
      unit: item?.unit ?? (section ? null : 'шт'),
    },
    save: (v) =>
      saveEquipItem(templateId.value, item?.id ?? null, {
        ...v,
        unit: section ? null : (v.unit as string),
      } as EquipItemBody),
    remove: item ? () => deleteEquipItem(item.id) : null,
  })
  if (result === undefined) return
  try {
    await loadItems()
  } catch (e) {
    loadError.value = shownError(e)
  }
  selectedId.value = result === 'deleted' ? null : result.id
}
</script>
