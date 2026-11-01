import type { RouteRecordRaw } from 'vue-router'

declare module 'vue-router' {
  interface RouteMeta {
    /** часть приложения: мобильная (PWA) или десктопная; по ней выполняется переход при смене режима устройства */
    part?: 'pwa' | 'desktop'
    /** страница на весь экран без левого меню, например редактор маршрута */
    hideMenu?: boolean
  }
}

// каждая часть приложения загружается одним чанком через свою точку входа:
// при первом переходе в часть загружаются все её страницы, дальше переходы внутри части без загрузки
const pwa = () => import('@/pwa/pwa')
const desktop = () => import('@/desktop/desktop')
const admin = () => import('@/admin/admin')

const routes: RouteRecordRaw[] = [
  {
    path: '/auth',
    component: () => import('@/layouts/AuthLayout.vue'),
    children: [{ path: '', name: 'login', component: () => import('@/auth/LoginPage.vue') }],
  },
  // мобильная часть (PWA): только этот префикс входит в scope service worker и manifest
  {
    path: '/m',
    meta: { part: 'pwa' },
    component: () => pwa().then((m) => m.PwaLayout),
    children: [{ path: '', name: 'pwa-home', component: () => pwa().then((m) => m.PwaHomePage) }],
  },
  // десктопная часть
  {
    path: '/',
    meta: { part: 'desktop' },
    component: () => desktop().then((m) => m.DesktopLayout),
    children: [
      { path: '', name: 'home', component: () => desktop().then((m) => m.DesktopHomePage) },
      { path: 'wagons', name: 'wagons', component: () => desktop().then((m) => m.WagonsPage) },
      { path: 'trips', name: 'trips', component: () => desktop().then((m) => m.TripsPage) },
      { path: 'routes', name: 'routes', component: () => desktop().then((m) => m.RoutesPage) },
      {
        path: 'routes/:id',
        name: 'route-edit',
        meta: { hideMenu: true },
        props: true,
        component: () => desktop().then((m) => m.RouteEditPage),
      },
      // администрирование: пункты меню десктопной части, страницы загружаются чанком admin при первом переходе
      { path: 'admin', name: 'admin-home', component: () => admin().then((m) => m.AdminHomePage) },
    ],
  },
]

export default routes
