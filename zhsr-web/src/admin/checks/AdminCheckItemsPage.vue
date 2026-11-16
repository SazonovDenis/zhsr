<template>
  <DesktopFrame v-model:floating="panel.floating.value" :back="back" :right-open="panel.open.value" right-width="28rem">
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
          {{ t('Добавить пункт') }}
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
      empty="В шаблоне нет пунктов"
      @row-dblclick="(r: ItemRow) => void edit(r.item, r.section)"
    />

    <template #right>
      <CheckItemPanel :item="selectedItem" :dicts="dicts" @close="panel.close" />
    </template>
  </DesktopFrame>
</template>

<script setup lang="ts">
import { computed, ref, shallowRef } from 'vue'
import { useRouter } from 'vue-router'

import type { CheckDict, CheckItem, CheckItemBody, CheckTemplate } from '@/api/checks'
import {
  CheckValueKind,
  deleteCheckItem,
  fetchAdminCheckTemplates,
  fetchCheckDicts,
  fetchCheckItems,
  saveCheckItem,
} from '@/api/checks'
import { t } from '@/boot/i18n'
import DesktopFrame from '@/desktop/frame/DesktopFrame.vue'
import { useSidePanel } from '@/desktop/frame/useSidePanel'
import { moveSortOrder } from '@/api/admin'
import { provideBusy } from '@/shared/busy'
import { valueKindText, VALUE_KINDS } from '@/shared/checks/labels'
import { treeRows } from '@/shared/checks/tree'
import DataTable from '@/shared/components/table/DataTable.vue'
import type { Column, RowKey, RowMenuItem } from '@/shared/components/table/types'
import type { ShownError } from '@/shared/errors'
import { shownError } from '@/shared/errors'
import CellTreeText from '../shared/CellTreeText.vue'
import { canMoveIn, orderSort, provideSortOrder, sortOrderColumn } from '../shared/sortOrder'
import { nextOrder, openSpecDialog, ORDER_FIELDS, TEXT_FIELDS, textValues } from '../shared/spec'
import CheckItemPanel from './CheckItemPanel.vue'

/**
 * Пункты шаблона чек-листа (Ф-ЧЕК-02): дерево разделов и пунктов со значением. «+ Раздел», «+ Пункт» и двойной клик
 * открывают карточку пункта; справа — нормативы выбранного пункта. Отключённый пункт зачёркнут.
 */
const props = defineProps<{ id: string }>()

interface ItemRow {
  id: number
  depth: number
  section: boolean
  text: string
  kind: string
  dict: string
  sortOrder: number
  item: CheckItem
}

const router = useRouter()
const busy = provideBusy()
const template = shallowRef<CheckTemplate | null>(null)
const items = shallowRef<CheckItem[]>([])
const dicts = shallowRef<CheckDict[]>([])
const selectedId = ref<RowKey | null>(null)
const panel = useSidePanel(selectedId)
const loadError = ref<ShownError | null>(null)
const templateId = computed(() => Number(props.id))

const title = computed(() =>
  template.value ? t('Пункты: {name}', { name: template.value.textRu }) : t('Пункты шаблона'),
)
const back = () => void router.push({ name: 'admin-check-templates' })

const rows = computed<ItemRow[]>(() =>
  treeRows(items.value).map(({ item, depth }) => ({
    id: item.id,
    depth,
    sortOrder: item.sortOrder,
    section: item.valueKind === null,
    text: item.textRu,
    kind: item.valueKind === null ? t('Раздел') : valueKindText(item.valueKind),
    dict: dicts.value.find((d) => d.id === item.checkDict)?.textRu ?? '',
    item,
  })),
)
const selectedItem = computed(() => items.value.find((i) => i.id === selectedId.value) ?? null)

const columns: Column<ItemRow>[] = [
  sortOrderColumn<ItemRow>('sortOrder'),
  { field: 'text', title: 'Текст', width: 'minmax(20rem, 3fr)', cell: CellTreeText },
  { field: 'kind', title: 'Вид значения', width: '10rem' },
  { field: 'dict', title: 'Шаблон ответов', width: 'minmax(10rem, 1fr)' },
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
    await moveSortOrder('check_list_template_item', row.id, up)
    await loadItems()
    selectedId.value = row.id
  },
})

async function loadItems() {
  items.value = await busy.run(fetchCheckItems(templateId.value))
}

// цепочка загрузок — сразу в setup: шаблон (заголовок), шаблоны ответов и пункты — параллельно
async function init() {
  loadError.value = null
  try {
    const [templates, dictList] = await busy.run(
      Promise.all([fetchAdminCheckTemplates(), fetchCheckDicts(), loadItems()]),
    )
    template.value = templates.find((x) => x.id === templateId.value) ?? null
    dicts.value = dictList
  } catch (e) {
    loadError.value = shownError(e)
  }
}
void init()

const rowMenu: RowMenuItem<ItemRow>[] = [
  { label: 'Изменить', run: (row) => void edit(row.item, row.section) },
  { label: 'Добавить пункт в раздел', visible: (row) => row.section, run: (row) => void edit(null, false, row.id) },
  { label: 'Добавить раздел', separator: true, run: () => void edit(null, true) },
  { label: 'Добавить пункт', run: () => void edit(null, false) },
]

/** Разделы шаблона, в которые можно поместить пункт: кроме самого пункта и его подразделов. */
function sectionChoices(item: CheckItem | null) {
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
    .filter(({ item: i }) => i.valueKind === null && !banned.has(i.id))
    .map(({ item: i, depth }) => ({ id: i.id, text: `${'— '.repeat(depth)}${i.textRu}` }))
}

/**
 * Карточка пункта: item = null — новый раздел (section) или пункт; parent — раздел нового пункта.
 */
async function edit(item: CheckItem | null, section: boolean, parent: number | null = null) {
  const siblings = items.value.filter((i) => i.parentItem === (item?.parentItem ?? parent))
  const dictChoices = dicts.value.map((d) => ({ id: d.id, text: d.textRu, disabled: d.disabled }))
  const result = await openSpecDialog<CheckItem>({
    title: item ? (section ? 'Раздел' : 'Пункт') : section ? 'Новый раздел' : 'Новый пункт',
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
            { name: 'valueKind', label: 'Вид значения', kind: 'single' as const, required: true, choices: VALUE_KINDS },
            {
              name: 'checkDict',
              label: 'Шаблон ответов',
              kind: 'single' as const,
              required: true,
              choices: dictChoices,
              visible: (v: Record<string, unknown>) => v.valueKind === CheckValueKind.DICT_VALUE,
            },
          ]),
      ...ORDER_FIELDS,
    ],
    initial: {
      ...textValues(item, nextOrder(siblings)),
      parentItem: item?.parentItem ?? parent,
      valueKind: item?.valueKind ?? (section ? null : CheckValueKind.DICT_VALUE),
      checkDict: item?.checkDict ?? null,
    },
    save: (v) =>
      saveCheckItem(templateId.value, item?.id ?? null, {
        ...v,
        valueKind: section ? null : (v.valueKind as number),
        checkDict: !section && v.valueKind === CheckValueKind.DICT_VALUE ? (v.checkDict as number) : null,
      } as CheckItemBody),
    remove: item ? () => deleteCheckItem(item.id) : null,
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
