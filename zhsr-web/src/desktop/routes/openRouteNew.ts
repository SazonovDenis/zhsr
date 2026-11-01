import type { Router } from 'vue-router'

import type { Route } from '@/api/routes'
import { openDialog } from '@/shared/components/dialog/openDialog'
import RouteNewDialog from './RouteNewDialog.vue'

/**
 * Открыть диалог «Новый маршрут» и, если маршрут создан, перейти в его редактор: остановки вводятся там.
 * Вызывается из списка маршрутов и из диалога «Создание рейса».
 */
export async function openRouteNew(router: Router): Promise<void> {
  const route = await openDialog<Route>(RouteNewDialog)
  if (route) await router.push({ name: 'route-edit', params: { id: route.id } })
}
