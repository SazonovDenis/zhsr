<template>
  <CbList v-model="model" single :placeholder="placeholder" :values="choices" :source="load" class="route-choice">
    <template #option="{ item }">
      <RouteOptionView :route="routeOptionOf(item)" class="route-option--list" />
    </template>
    <!-- compact: в поле — только номер и сообщение, строка остаётся одной высоты с соседними полями (FilterBar) -->
    <template v-if="!compact" #value="{ item }">
      <RouteOptionView :route="routeOptionOf(item)" />
    </template>
  </CbList>
</template>

<script setup lang="ts">
import { computed, shallowRef } from 'vue'

import type { RouteOption } from '@/api/routes'
import { fetchRouteOptions } from '@/api/routes'
import type { ChoiceModel } from '@/shared/components/choice/choice'
import CbList from '@/shared/components/choice/CbList.vue'
import { useLoad } from '@/shared/loadable'
import { dictManager } from '@/shared/dict/DictManager'
import { routeOptionItem, routeOptionOf } from './routeOptionItem'
import RouteOptionView from './RouteOptionView.vue'

/**
 * Выбор маршрута: CbList, у которого строка списка и выбранное значение показаны маршрутом по макету (RouteOptionView).
 * Поиск — по номеру поезда, сообщению и всем станциям маршрута, включая станции следования.
 *
 *   <RouteSelect v-model="draft.route" :routes="routes" />
 *   <RouteSelect v-model="filter.route" placeholder="Маршрут" compact />
 *
 * - routes — из каких маршрутов выбирать; не задано — из всех маршрутов, действующих и завершённых.
 * - compact — в поле только номер и сообщение одной строкой: для панели фильтров.
 * - v-model — id маршрута; не выбран — null.
 */
const props = withDefaults(defineProps<{ routes?: RouteOption[] | null; placeholder?: string; compact?: boolean }>(), {
  routes: null,
  placeholder: 'Выберите…',
  compact: false,
})

const model = defineModel<ChoiceModel>({ default: null })

const allRoutes = shallowRef<RouteOption[]>([])

const choices = computed(() => (props.routes ?? allRoutes.value).map(routeOptionItem))

// варианты: маршруты и станции для строк вариантов (остановки) — параллельно, всё — загрузка выпадающего списка
const load = useLoad(async () => {
  const [, list] = await Promise.all([
    dictManager.prepareDict('Station'),
    props.routes ? null : fetchRouteOptions({ kinds: [], states: [] }),
  ])
  if (list) allRoutes.value = list
})
void load.run()
</script>
