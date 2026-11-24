<template>
  <DesktopFrame v-model:floating="panel.floating.value" :right-open="panel.open.value">
    <template #header>
      <h1 class="desktop-frame__title">{{ t('Поезда') }}</h1>
      <div class="desktop-frame__actions">
        <q-btn
          unelevated
          no-caps
          color="primary"
          :disable="!!createReason"
          :title="createReason || undefined"
          @click="create"
        >
          {{ t('Создать поезд') }}
        </q-btn>
      </div>
    </template>

    <FilterBar>
      <TextInput v-model="filter.search" placeholder="Поезд или номер вагона" width="14rem" />
      <CbButton v-model="filter.states" label="Состояние" :values="states" />
      <template #right>{{ t('{n} поездов', rows.length) }}</template>
    </FilterBar>

    <DataTable
      v-model:selected="selectedId"
      :columns="trainsColumns"
      :rows="rows"
      :error="loadError"
      :retry="init"
      row-key="id"
      :loading="busy.active.value"
      :row-menu="rowMenu"
      empty="Поездов по выбранному фильтру нет"
      @row-dblclick="open"
    />

    <template #right>
      <TrainPanel
        :train="selected"
        :edit-reason="selected ? editReason(selected) : ''"
        @close="panel.close"
        @open="selected && open(selected)"
        @edit="selected && rename(selected)"
      />
    </template>
  </DesktopFrame>
</template>

<script setup lang="ts">
import { computed, reactive, ref, shallowRef, watch } from 'vue'
import { useRouter } from 'vue-router'

import type { TrainFilter, TrainRow } from '@/api/trains'
import { deleteTrain, fetchTrains } from '@/api/trains'
import { t } from '@/boot/i18n'
import { ActiveState } from '@/shared/constants/ActiveState'
import { provideBusy } from '@/shared/busy'
import type { ChoiceItem } from '@/shared/components/choice/choice'
import CbButton from '@/shared/components/choice/CbButton.vue'
import { confirmDelete } from '@/shared/components/dialog/confirmDelete'
import { openDialog } from '@/shared/components/dialog/openDialog'
import FilterBar from '@/shared/components/FilterBar.vue'
import TextInput from '@/shared/components/form/TextInput.vue'
import DataTable from '@/shared/components/table/DataTable.vue'
import type { RowMenuItem } from '@/shared/components/table/types'
import { RoleType } from '@/shared/constants/systemDicts'
import { dictManager } from '@/shared/dict/DictManager'
import type { ShownError } from '@/shared/errors'
import { shownError } from '@/shared/errors'
import { roleReason } from '@/shared/roleReason'
import DesktopFrame from '../frame/DesktopFrame.vue'
import { useSidePanel } from '../frame/useSidePanel'
import { openTrainNew } from './openTrainNew'
import TrainNameDialog from './TrainNameDialog.vue'
import TrainPanel from './TrainPanel.vue'
import { trainsColumns } from './trainsColumns'
import { useKeptSelection } from '../frame/keptSelection'

/**
 * Список поездов (Ф-ПОЕ-00): фильтр по состоянию и поиск по обозначению поезда и номеру вагона, таблица и правая
 * панель поезда. Двойной клик по строке или «Состав поезда» на панели открывает экран состава поезда. Поезд создают
 * планировщик вагонов и администратор (Ф-ПОЕ-01).
 */

const router = useRouter()

const createReason = computed(() => roleReason([RoleType.WAGON_PLANNER], 'Создаёт планировщик вагонов'))

const states: ChoiceItem[] = [
  { id: ActiveState.ACTIVE, text: 'Действует' },
  { id: ActiveState.CLOSED, text: 'Расформирован' },
]

const filter = reactive<TrainFilter>({ states: [ActiveState.ACTIVE], search: '' })

const rows = shallowRef<TrainRow[]>([])
// общее ожидание страницы: спиннер на таблице — на всю цепочку загрузок (справочник станций, список)
const busy = provideBusy()
// выбранная строка остаётся при возврате на страницу
const selectedId = useKeptSelection()
const panel = useSidePanel(selectedId)
const selected = computed(() => rows.value.find((row) => row.id === selectedId.value) ?? null)

// ошибка загрузки показывается на месте строк таблицы
const loadError = ref<ShownError | null>(null)

async function load() {
  loadError.value = null
  try {
    rows.value = await busy.run(fetchTrains({ ...filter, search: filter.search.trim() }))
  } catch (e) {
    rows.value = []
    loadError.value = shownError(e)
  }
}

watch(filter, load, { deep: true })

function open(train: TrainRow) {
  void router.push({ name: 'train-form', params: { id: train.id } })
}

function create() {
  void openTrainNew(router)
}

/** Почему поезд не исправить: нет прав или поезд расформирован; пусто — можно (как canEdit карточки поезда). */
function editReason(tr: TrainRow): string {
  return (
    roleReason([RoleType.WAGON_PLANNER], 'Поезд меняет планировщик вагонов') ||
    (tr.dend ? t('Поезд расформирован') : '')
  )
}

/** «Редактировать» — обозначение поезда; сохранили — список заново. */
async function rename(tr: TrainRow) {
  const saved = await openDialog<TrainRow>(TrainNameDialog, { train: { id: tr.id, name: tr.name } })
  if (saved) await load()
}

const rowMenu: RowMenuItem<TrainRow>[] = [
  { label: 'Состав поезда', run: open },
  // удалить можно поезд без рейсов, в котором никогда не было вагонов: иначе причина от сервера в окне
  {
    label: 'Удалить',
    separator: true,
    disabled: () => roleReason([RoleType.WAGON_PLANNER], 'Удаляет планировщик вагонов'),
    run: (tr: TrainRow) =>
      void confirmDelete('Удаление поезда', 'Удалить поезд {name}?', { name: tr.name }, () => deleteTrain(tr.id)).then(
        (ok) => {
          if (ok) void load()
        },
      ),
  },
  { label: 'Создать поезд', separator: true, disabled: () => createReason.value, run: () => void create() },
]

// цепочка загрузок — сразу в setup: спиннер виден с первой отрисовки; она же — повтор по клику на ошибку в таблице
async function init() {
  await Promise.all([loadDicts(), load()])
}

async function loadDicts() {
  try {
    await busy.run(
      dictManager.prepareDicts('Station', 'Depot', 'Yard', 'LocationKind', 'WagonType', 'WagonModel', 'TripState'),
    )
  } catch (e) {
    loadError.value = shownError(e)
  }
}
void init()
</script>
