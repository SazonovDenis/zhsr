<template>
  <CbList v-model="model" single :placeholder="placeholder" :values="choices" class="route-choice">
    <template #option="{ item }">
      <RouteOption :route="routeOf(item)" class="route-option--list" />
    </template>
    <!-- compact: в поле — только номер и сообщение, строка остаётся одной высоты с соседними полями (FilterBar) -->
    <template v-if="!compact" #value="{ item }">
      <RouteOption :route="routeOf(item)" />
    </template>
  </CbList>
</template>

<script setup lang="ts">
import { computed, onMounted, shallowRef } from 'vue'

import type { Route } from '@/api/routes'
import { fetchRoutes } from '@/api/routes'
import type { ChoiceModel } from '@/shared/components/choice/choice'
import CbList from '@/shared/components/choice/CbList.vue'
import { dictManager } from '@/shared/dict/DictManager'
import { routeChoiceItem, routeOf } from './routeChoice'
import RouteOption from './RouteOption.vue'

/**
 * Выбор маршрута: CbList, у которого строка списка и выбранное значение показаны маршрутом по макету (RouteOption).
 * Поиск — по номеру поезда, сообщению и всем станциям маршрута, включая станции следования.
 *
 *   <RouteChoice v-model="draft.route" :routes="routes" />
 *   <RouteChoice v-model="filter.route" placeholder="Маршрут" compact />
 *
 * - routes — из каких маршрутов выбирать; не задано — из всех маршрутов, действующих и завершённых.
 * - compact — в поле только номер и сообщение одной строкой: для панели фильтров.
 * - v-model — id маршрута; не выбран — null.
 */
const props = withDefaults(defineProps<{ routes?: Route[] | null; placeholder?: string; compact?: boolean }>(), {
  routes: null,
  placeholder: 'Выберите…',
  compact: false,
})

const model = defineModel<ChoiceModel>({ default: null })

const allRoutes = shallowRef<Route[]>([])

const choices = computed(() => (props.routes ?? allRoutes.value).map(routeChoiceItem))

onMounted(async () => {
  await dictManager.prepareDicts('RouteKind', 'Station')
  if (!props.routes) allRoutes.value = await fetchRoutes({ kinds: [], states: [] }, null)
})
</script>
