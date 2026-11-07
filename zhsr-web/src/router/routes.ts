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
    /** мобильная часть: страница с нижней панелью вкладок («Рейс», «Заявки», «Профиль») */
    tabs?: boolean
    /** мобильная часть: заголовок окна в шапке, на русском; нет — название системы */
    title?: string
  }
}

// каждая часть приложения загружается одним чанком через свою точку входа:
// при первом переходе в часть загружаются все её страницы, дальше переходы внутри части без загрузки
const pwa = () => import('@/pwa/pwa')
const desktop = () => import('@/desktop/desktop')
const admin = () => import('@/admin/admin')

/**
 * Таблицы общего редактора администрирования (их перечень и поля задаёт сервер: AdminTables). Маршрут таблицы —
 * admin-<таблица>, на него ссылается пункт меню.
 */
const ADMIN_TABLES = ['depot', 'yard', 'station', 'stage', 'wagon_type', 'model_type', 'fault_group_type', 'fault_type']

const routes: RouteRecordRaw[] = [
  {
    path: '/auth',
    component: () => import('@/layouts/AuthLayout.vue'),
    children: [
      { path: '', name: 'login', component: () => import('@/auth/LoginPage.vue') },
      // смена пароля: сюда переводит ответ сервера 406 — пароль задан администратором или пароль по умолчанию
      { path: 'password', name: 'change-password', component: () => import('@/auth/ChangePasswordPage.vue') },
    ],
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
        meta: { tabs: true, title: 'Текущий рейс' },
        component: () => pwa().then((m) => m.PwaHomePage),
      },
      {
        path: 'requests',
        name: 'pwa-requests',
        meta: { tabs: true, pair: 'requests', title: 'Заявки' },
        component: () => pwa().then((m) => m.PwaRequestsPage),
      },
      {
        path: 'requests/new',
        name: 'pwa-request-new',
        meta: { pair: 'requests', title: 'Новая заявка' },
        component: () => pwa().then((m) => m.PwaRequestWizardPage),
      },
      {
        path: 'requests/:id',
        name: 'pwa-request',
        meta: { pair: 'requests', title: 'Заявка' },
        props: true,
        component: () => pwa().then((m) => m.PwaRequestPage),
      },
      {
        path: 'requests/:id/edit',
        name: 'pwa-request-edit',
        meta: { pair: 'requests', title: 'Редактирование заявки' },
        props: true,
        component: () => pwa().then((m) => m.PwaRequestWizardPage),
      },
      {
        path: 'profile',
        name: 'pwa-profile',
        meta: { tabs: true, title: 'Профиль' },
        component: () => pwa().then((m) => m.PwaProfilePage),
      },
      {
        path: 'trip',
        name: 'pwa-trip',
        meta: { pair: 'trips', title: 'Формирование рейса' },
        component: () => pwa().then((m) => m.PwaTripCrewPage),
      },
      {
        path: 'trip/:id/coupling/:kind',
        name: 'pwa-trip-coupling',
        meta: { pair: 'trips' },
        props: true,
        component: () => pwa().then((m) => m.PwaCouplingPage),
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
      {
        path: 'trips/:id',
        name: 'trip-form',
        meta: { hideMenu: true, pair: 'pwa-trip' },
        props: true,
        component: () => desktop().then((m) => m.TripFormPage),
      },
      { path: 'acts', name: 'acts', component: () => desktop().then((m) => m.ActsPage) },
      {
        path: 'acts/new',
        name: 'transfer',
        meta: { hideMenu: true },
        component: () => desktop().then((m) => m.TransferPage),
      },
      {
        path: 'trips/:id/coupling/:kind',
        name: 'trip-coupling',
        meta: { hideMenu: true },
        props: true,
        component: () => desktop().then((m) => m.TripCouplingPage),
      },
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
      { path: 'admin/users', name: 'admin-users', component: () => admin().then((m) => m.AdminUsersPage) },
      ...ADMIN_TABLES.map((table) => ({
        path: `admin/tables/${table}`,
        name: `admin-${table}`,
        props: { table },
        component: () => admin().then((m) => m.AdminTablePage),
      })),
    ],
  },
]

export default routes
