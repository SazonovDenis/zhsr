import type { RouteRecordRaw } from 'vue-router'

declare module 'vue-router' {
  interface RouteMeta {
    /** часть приложения: мобильная (PWA) или десктопная; по ней выполняется переход при смене режима устройства */
    part?: 'pwa' | 'desktop'
    /** страница на весь экран без левого меню, например редактор маршрута */
    hideMenu?: boolean
    /**
     * имя парного маршрута в другой части: при смене режима устройства (например, эмуляция в DevTools) переход идёт
     * на него, а не на главную части
     */
    pair?: string
    /** мобильная часть: страница с нижней панелью вкладок («Сегодня», «Заявки», «+ Заявка», «Профиль») */
    tabs?: boolean
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
    children: [
      {
        path: '',
        name: 'pwa-home',
        meta: { tabs: true },
        component: () => pwa().then((m) => m.PwaHomePage),
      },
      {
        path: 'requests',
        name: 'pwa-requests',
        meta: { tabs: true, pair: 'requests' },
        component: () => pwa().then((m) => m.PwaRequestsPage),
      },
      {
        path: 'requests/new',
        name: 'pwa-request-new',
        meta: { pair: 'requests' },
        component: () => pwa().then((m) => m.PwaRequestWizardPage),
      },
      {
        path: 'requests/:id',
        name: 'pwa-request',
        meta: { pair: 'requests' },
        props: true,
        component: () => pwa().then((m) => m.PwaRequestPage),
      },
      {
        path: 'requests/:id/edit',
        name: 'pwa-request-edit',
        meta: { pair: 'requests' },
        props: true,
        component: () => pwa().then((m) => m.PwaRequestWizardPage),
      },
      {
        path: 'profile',
        name: 'pwa-profile',
        meta: { tabs: true },
        component: () => pwa().then((m) => m.PwaProfilePage),
      },
      {
        path: 'trip',
        name: 'pwa-trip',
        meta: { pair: 'trips' },
        component: () => pwa().then((m) => m.PwaTripCrewPage),
      },
    ],
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
        path: 'requests',
        name: 'requests',
        meta: { pair: 'pwa-requests' },
        component: () => desktop().then((m) => m.RequestsPage),
      },
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
