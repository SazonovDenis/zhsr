<template>
  <DesktopFrame>
    <template #header>
      <h1 class="desktop-frame__title">{{ t('Шаблоны ответов') }}</h1>
      <div class="desktop-frame__actions">
        <q-btn
          unelevated
          outline
          no-caps
          color="ink"
          class="zs-soft-border"
          :disable="!selected"
          :title="selected ? undefined : t('Выберите шаблон ответов в списке')"
          @click="selected && openValues(selected)"
        >
          {{ t('Варианты ответов') }}
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
      :struck="(d: CheckDict) => d.disabled"
      :loading="busy.active.value"
      :error="loadError"
      :retry="load"
      :row-menu="rowMenu"
      empty="Шаблонов ответов нет"
      @row-dblclick="edit"
    />
  </DesktopFrame>
</template>

<script setup lang="ts">
import { computed, ref, shallowRef } from 'vue'

import type { CheckDict } from '@/api/checks'
import { deleteCheckDict, fetchCheckDicts, saveCheckDict } from '@/api/checks'
import { t } from '@/boot/i18n'
import DesktopFrame from '@/desktop/frame/DesktopFrame.vue'
import { moveSortOrder } from '@/api/admin'
import { provideBusy } from '@/shared/busy'
import { openDialog } from '@/shared/components/dialog/openDialog'
import { confirmDelete } from '@/shared/components/dialog/confirmDelete'
import DataTable from '@/shared/components/table/DataTable.vue'
import type { RowMenuItem } from '@/shared/components/table/types'
import type { ShownError } from '@/shared/errors'
import { shownError } from '@/shared/errors'
import { nextOrder, openSpecDialog, ORDER_FIELDS, TEXT_FIELDS, textValues } from '../shared/spec'
import CheckDictValuesDialog from './CheckDictValuesDialog.vue'
import { canMoveIn, orderSort, provideSortOrder } from '../shared/sortOrder'
import { textColumns } from './textColumns'
import { useKeptSelection } from '@/desktop/frame/keptSelection'

/**
 * Шаблоны ответов чек-листа (Ф-ЧЕК-01): «+ Шаблон» заводит шаблон ответов, «Варианты ответов» открывает варианты
 * выбранного шаблона. Двойной клик — правка шаблона; отключённый шаблон зачёркнут.
 */

const busy = provideBusy()
const rows = shallowRef<CheckDict[]>([])
// выбранная строка остаётся при возврате на страницу
const selectedId = useKeptSelection()
const loadError = ref<ShownError | null>(null)
const selected = computed(() => rows.value.find((r) => r.id === selectedId.value) ?? null)
const columns = textColumns<CheckDict>()

// перестановка строк кнопками в колонке «Порядок»: список всегда в порядке показа
provideSortOrder<CheckDict>({
  reason: () => '',
  canMove: (row, up) => canMoveIn(rows.value, row, up),
  move: async (row, up) => {
    await moveSortOrder('check_dict', row.id, up)
    await load()
    selectedId.value = row.id
  },
})

async function load() {
  loadError.value = null
  try {
    rows.value = await busy.run(fetchCheckDicts())
  } catch (e) {
    rows.value = []
    loadError.value = shownError(e)
  }
}
void load()

const rowMenu: RowMenuItem<CheckDict>[] = [
  { label: 'Варианты ответов', run: (row) => void openValues(row) },
  { label: 'Изменить', run: (row) => void edit(row) },
  { label: 'Удалить', run: (row) => void remove(row) },
  { label: 'Создать шаблон', separator: true, run: () => void edit(null) },
]

async function edit(dict: CheckDict | null) {
  const result = await openSpecDialog<CheckDict>({
    title: dict ? 'Шаблон ответов' : 'Новый шаблон ответов',
    okText: dict ? 'Сохранить' : 'Создать',
    fields: [...TEXT_FIELDS, ...ORDER_FIELDS],
    initial: textValues(dict, nextOrder(rows.value)),
    save: (v) => saveCheckDict(dict?.id ?? null, v as never),
    remove: dict ? () => deleteCheckDict(dict.id) : null,
  })
  if (result === undefined) return
  await load()
  selectedId.value = result === 'deleted' ? null : result.id
}

async function openValues(dict: CheckDict) {
  await openDialog(CheckDictValuesDialog, { dict })
}

async function remove(row: CheckDict) {
  if (!(await confirmDelete('Удаление записи', 'Удалить запись «{name}»?', { name: row.textRu }, () => deleteCheckDict(row.id)))) return
  await load()
  selectedId.value = null
}
</script>
