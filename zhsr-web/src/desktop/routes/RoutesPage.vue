<template>
  <DesktopFrame v-model:floating="panel.floating.value" :right-open="panel.open.value">
    <template #header>
      <h1 class="desktop-frame__title">{{ t('Маршруты') }}</h1>
      <div class="desktop-frame__actions">
        <q-btn
          unelevated
          no-caps
          color="primary"
          :disable="!!createReason"
          :title="createReason || undefined"
          @click="openRouteNew(router)"
        >
          {{ t('Новый маршрут') }}
        </q-btn>
      </div>
    </template>

    <FilterBar>
      <CbButton v-model="filter.kinds" label="Вид" :values="dictChoices('RouteKind')" />
      <CbButton v-model="filter.states" label="Состояние" :values="states" />
      <template #right>{{ t('{n} маршрутов', rows.length) }}</template>
    </FilterBar>

    <DataTable
      v-model:selected="selectedId"
      :columns="routesColumns"
      :rows="rows"
      :error="loadError"
      :retry="init"
      row-key="id"
      :sort="sort"
      :loading="busy.active.value"
      :row-menu="rowMenu"
      empty="Маршрутов по выбранному фильтру нет"
      @sort="sort = $event"
      @row-dblclick="edit"
    />

    <template #right>
      <RoutePanel :route="selected" @close="panel.close" @edit="selected && edit(selected)" />
    </template>
  </DesktopFrame>
</template>

<script setup lang="ts">
import { computed, reactive, ref, shallowRef, watch } from 'vue'
import { useRouter } from 'vue-router'

import type { RouteFilter, RouteRow } from '@/api/routes'
import { deleteRoute, fetchRoutes } from '@/api/routes'
import { t } from '@/boot/i18n'
import type { ChoiceItem } from '@/shared/components/choice/choice'
import CbButton from '@/shared/components/choice/CbButton.vue'
import { confirmDelete } from '@/shared/components/dialog/confirmDelete'
import type { RowMenuItem, TableSort } from '@/shared/components/table/types'
import DataTable from '@/shared/components/table/DataTable.vue'
import type { ShownError } from '@/shared/errors'
import { shownError } from '@/shared/errors'
import FilterBar from '@/shared/components/FilterBar.vue'
import { dictChoices } from '@/shared/dict/dictChoices'
import { ActiveState } from '@/shared/constants/ActiveState'
import { provideBusy } from '@/shared/busy'
import { dictManager } from '@/shared/dict/DictManager'
import { RoleType } from '@/shared/constants/systemDicts'
import { roleReason } from '@/shared/roleReason'
import DesktopFrame from '../frame/DesktopFrame.vue'
import { useSidePanel } from '../frame/useSidePanel'
import { openRouteNew } from './openRouteNew'
import RoutePanel from './RoutePanel.vue'
import { routesColumns } from './routesColumns'
import { useKeptSelection } from '../frame/keptSelection'

/**
 * Список маршрутов: фильтры, таблица и правая панель выбранного маршрута. Двойной клик по строке или
 * «Редактировать» на панели открывает редактор маршрута.
 */

const router = useRouter()

// маршрут создают администратор и планировщик вагонов (Ф-МАР-01)
const createReason = computed(() => roleReason([RoleType.WAGON_PLANNER], 'Создаёт планировщик вагонов'))

const states: ChoiceItem[] = [
  { id: ActiveState.ACTIVE, text: 'Действует' },
  { id: ActiveState.CLOSED, text: 'Завершён' },
]

const filter = reactive<RouteFilter>({ kinds: [], states: [ActiveState.ACTIVE] })

const rows = shallowRef<RouteRow[]>([])
// общее ожидание страницы: спиннер на таблице — на всю цепочку загрузок (справочники, фильтры, список)
const busy = provideBusy()
// выбранная строка остаётся при возврате на страницу
const selectedId = useKeptSelection()
// правая панель: рядом со списком на широком окне, поверх него — на узком
const panel = useSidePanel(selectedId)
const selected = computed(() => rows.value.find((row) => row.id === selectedId.value) ?? null)
const sort = ref<TableSort | null>(null)

// ошибка загрузки показывается на месте строк таблицы
const loadError = ref<ShownError | null>(null)

async function load() {
  loadError.value = null
  try {
    rows.value = await busy.run(fetchRoutes(filter, sort.value))
  } catch (e) {
    rows.value = []
    loadError.value = shownError(e)
  }
}

// фильтр или сортировка изменились — данные загружаются заново
watch([filter, sort], load, { deep: true })

function edit(route: RouteRow) {
  void router.push({ name: 'route-edit', params: { id: route.id } })
}

// меню строки: то же, что кнопка панели маршрута, затем действие со списком «Новый маршрут»
const rowMenu: RowMenuItem<RouteRow>[] = [
  {
    label: 'Редактировать',
    disabled: () => roleReason([RoleType.WAGON_PLANNER], 'Правит планировщик вагонов'),
    run: edit,
  },
  // удалить можно маршрут без рейсов: иначе причина от сервера в окне
  {
    label: 'Удалить',
    separator: true,
    disabled: () => roleReason([RoleType.WAGON_PLANNER], 'Удаляет планировщик вагонов'),
    run: (r: RouteRow) =>
      void confirmDelete(
        'Удаление маршрута',
        'Удалить маршрут № {number} {name}?',
        { number: r.trainNumber, name: r.name },
        () => deleteRoute(r.id),
      ).then((ok) => {
        if (ok) void load()
      }),
  },
  { label: 'Новый маршрут', separator: true, disabled: () => createReason.value, run: () => void openRouteNew(router) },
]

// цепочка загрузок — сразу в setup: спиннер виден с первой отрисовки; она же — повтор по клику на ошибку в таблице
async function init() {
  // станции для строк (остановки маршрута) и сами строки — независимо, параллельно
  await Promise.all([loadDicts(), load()])
}

async function loadDicts() {
  try {
    await busy.run(dictManager.prepareDict('Station'))
  } catch (e) {
    loadError.value = shownError(e)
  }
}
void init()
</script>
