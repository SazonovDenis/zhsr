<template>
  <div class="desktop-layout">
    <DesktopMenu v-show="!route.meta.hideMenu" />
    <main class="desktop-layout__main">
      <router-view />
    </main>
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import type { BusEvents } from '@/shared/bus'
import { bus } from '@/shared/bus'
import { allMenuItems, menuEvent } from './menu/menu'
import DesktopMenu from './menu/DesktopMenu.vue'

/**
 * Каркас десктопной части: левое меню и место для фрейма страницы. Страница с meta.hideMenu (редактор, в который
 * «проваливаются» из списка) показывается на весь экран, без левого меню.
 *
 * Layout слушает события пунктов меню `menu-<id>` и переходит на маршрут пункта. Пункт без маршрута событие
 * отправляет, но перехода нет: его обрабатывает тот, кто на это событие подписан.
 */

const router = useRouter()
const route = useRoute()

const routeByEvent = new Map<string, string>(
  allMenuItems().flatMap((item) => (item.route ? [[menuEvent(item), item.route] as const] : [])),
)

function onBusEvent(type: keyof BusEvents) {
  const name = routeByEvent.get(type)
  if (name) void router.push({ name })
}

bus.on('*', onBusEvent)
onBeforeUnmount(() => bus.off('*', onBusEvent))
</script>

<style scoped lang="scss">
.desktop-layout {
  display: flex;
  height: 100vh;
}

.desktop-layout__main {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
}
</style>
