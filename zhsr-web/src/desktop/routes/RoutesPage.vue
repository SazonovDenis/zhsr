<template>
  <DesktopFrame v-model:floating="panel.floating.value" :right-open="panel.open.value">
    <template #header>
      <h1 class="desktop-frame__title">{{ t('Маршруты') }}</h1>
      <div class="desktop-frame__actions">
        <button type="button" class="zs-btn-primary" @click="openRouteNew(router)">{{ t('Новый маршрут') }}</button>
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
      row-key="id"
      :sort="sort"
      :loading="loading"
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
import { computed, onMounted, reactive, ref, shallowRef, watch } from 'vue'
import { useRouter } from 'vue-router'

import type { Route, RouteFilter } from '@/api/routes'
import { fetchRoutes } from '@/api/routes'
import { t } from '@/boot/i18n'
import type { ChoiceItem } from '@/shared/components/choice/choice'
import CbButton from '@/shared/components/choice/CbButton.vue'
import type { RowKey, TableSort } from '@/shared/components/table/types'
import DataTable from '@/shared/components/table/DataTable.vue'
import FilterBar from '@/shared/components/FilterBar.vue'
import { dictChoices } from '@/shared/dict/dictChoices'
import { dictManager } from '@/shared/dict/DictManager'
import DesktopFrame from '../frame/DesktopFrame.vue'
import { useSidePanel } from '../frame/useSidePanel'
import { openRouteNew } from './openRouteNew'
import RoutePanel from './RoutePanel.vue'
import { routesColumns } from './routesColumns'

/**
 * Список маршрутов: фильтры, таблица и правая панель выбранного маршрута. Двойной клик по строке или
 * «Редактировать» на панели открывает редактор маршрута.
 */

const router = useRouter()

const states: ChoiceItem[] = [
  { id: 'active', text: 'Действует' },
  { id: 'closed', text: 'Завершён' },
]

const filter = reactive<RouteFilter>({ kinds: [], states: ['active'] })

const rows = shallowRef<Route[]>([])
const loading = ref(false)
const selectedId = ref<RowKey | null>(null)
// правая панель: рядом со списком на широком окне, поверх него — на узком
const panel = useSidePanel(selectedId)
const selected = computed(() => rows.value.find((row) => row.id === selectedId.value) ?? null)
const sort = ref<TableSort | null>(null)

async function load() {
  loading.value = true
  try {
    rows.value = await fetchRoutes(filter, sort.value)
  } finally {
    loading.value = false
  }
}

// фильтр или сортировка изменились — данные загружаются заново
watch([filter, sort], load, { deep: true })

function edit(route: Route) {
  void router.push({ name: 'route-edit', params: { id: route.id } })
}

onMounted(async () => {
  await dictManager.prepareDicts('RouteKind', 'Station')
  await load()
})
</script>
