import { defineRouter } from '#q-app'
import { watch } from 'vue'
import type { RouteLocationNormalized, RouteLocationRaw } from 'vue-router'
import { createMemoryHistory, createRouter, createWebHashHistory, createWebHistory } from 'vue-router'

import { onLoginRequired, onPasswordChangeRequired } from '@/api/http'
import { isMobile } from '@/shared/device'
import { currentUser } from '@/shared/user'
import routes from './routes'

/**
 * Куда перейти, если часть приложения у маршрута не соответствует режиму устройства: на парный маршрут другой части
 * (meta.pair, например «Заявки» /requests ↔ /m/requests), а если пары нет — на главную части.
 */
function partRedirect(route: RouteLocationNormalized): RouteLocationRaw | undefined {
  const part = route.meta.part
  if (!part) return
  if (isMobile.value && part !== 'pwa') return { name: route.meta.pair ?? 'pwa-home' }
  if (!isMobile.value && part === 'pwa') return { name: route.meta.pair ?? 'home' }
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

  // сервер требует сменить пароль (ответ 406) — на страницу смены пароля
  onPasswordChangeRequired(() => {
    if (router.currentRoute.value.name !== 'change-password') void router.push({ name: 'change-password' })
  })

  // нет входа (ответ 401: сеанс не открыт или истёк) — на страницу входа; пользователь забывается: войти может другой
  onLoginRequired(() => {
    currentUser.value = null
    if (router.currentRoute.value.name !== 'login') void router.push({ name: 'login' })
  })

  // режим устройства сменился (например, включена эмуляция в DevTools) — переходим в соответствующую часть
  watch(isMobile, () => {
    const target = partRedirect(router.currentRoute.value)
    if (target) void router.replace(target)
  })

  return router
})
