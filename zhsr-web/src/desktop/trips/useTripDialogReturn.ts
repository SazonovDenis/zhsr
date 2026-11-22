import { watch } from 'vue'
import { useRouter } from 'vue-router'

import type { TripRow } from '@/api/trips'
import { openDialog } from '@/shared/components/dialog/openDialog'
import { pageKey, tripDialogReopen } from './tripDialogReturn'
import TripNewDialog from './TripNewDialog.vue'

/**
 * Страница, на которой открывается окно рейса: окно закрывалось на время создания нового маршрута или поезда — оно
 * открывается снова с тем, что было введено (reopenTripDialog). saved — рейс сохранён в этом окне. Вызывается в setup.
 */
export function useTripDialogReturn(saved: (trip: TripRow) => void) {
  const router = useRouter()
  watch(
    tripDialogReopen,
    async (reopen) => {
      if (!reopen || reopen.page !== pageKey(router.currentRoute.value)) return
      tripDialogReopen.value = null
      const trip = await openDialog<TripRow>(TripNewDialog, { trip: reopen.trip, draft: reopen.draft })
      if (trip) saved(trip)
    },
    { immediate: true },
  )
}
