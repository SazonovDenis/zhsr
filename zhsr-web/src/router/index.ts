import { defineRouter } from '#q-app'
import { watch } from 'vue'
import type { RouteLocationNormalized, RouteLocationRaw } from 'vue-router'
import { createMemoryHistory, createRouter, createWebHashHistory, createWebHistory } from 'vue-router'

import { isMobile } from '@/shared/device'
import routes from './routes'

/** Куда перейти, если часть приложения у маршрута не соответствует режиму устройства. */
function partRedirect(route: RouteLocationNormalized): RouteLocationRaw | undefined {
  const part = route.meta.part
  if (!part) return
  if (isMobile.value && part !== 'pwa') return { name: 'pwa-home' }
  if (!isMobile.value && part === 'pwa') return { name: 'home' }
}

export default defineRouter(() => {
  const createHistory = import.meta.env.QUASAR_SERVER
    ? createMemoryHistory
    : import.meta.env.QUASAR_VUE_ROUTER_MODE === 'history'
      ? createWebHistory
      : createWebHashHistory

  const router = createRouter({
    scrollBehavior: () => ({ left: 0, top: 0 }),
    routes,
    // режим и базовый путь задаются в quasar.config.ts > build
    history: createHistory(import.meta.env.QUASAR_VUE_ROUTER_BASE),
  })

  // на телефоне открывается мобильная часть, на компьютере — десктопная
  router.beforeEach(partRedirect)

  // режим устройства сменился (например, включена эмуляция в DevTools) — переходим в соответствующую часть
  watch(isMobile, () => {
    const target = partRedirect(router.currentRoute.value)
    if (target) void router.replace(target)
  })

  return router
})
