import { Notify } from 'quasar'
import { useRouter } from 'vue-router'

import type { TripCrew, TripRow } from '@/api/trips'
import { t } from '@/boot/i18n'
import { openDialog } from '@/shared/components/dialog/openDialog'
import type { RowAction } from '@/shared/components/table/rowMenu'
import { CrewDuty, RoleType, TripStatus } from '@/shared/constants/systemDicts'
import { dictManager } from '@/shared/dict/DictManager'
import { currentUser, hasRole, loadCurrentUser } from '@/shared/user'
import { markEditable, useNow } from '@/shared/trips/tripTimes'
import TripCancelDialog from './TripCancelDialog.vue'
import TripMarkDialog from './TripMarkDialog.vue'
import TripNewDialog from './TripNewDialog.vue'

/** Тексты действий с рейсом по порядку кнопок. */
export const TRIP_ACTION_LABELS = [
  'Формирование',
  'Редактировать',
  'Отметить отправление',
  'Отметить прибытие',
  'Исправить отправление',
  'Исправить прибытие',
  'Отцепка',
  'Прицепка',
  'Отменить рейс',
]

const FIX_REASON = 'Исправить можно в течение 10 минут после отметки'
const PLANNER_REASON = 'Только планировщик вагонов'
const MARK_REASON = 'Отмечает ЛНП этого рейса'
const COUPLE_REASON = 'Акт составляет ЛНП этого рейса или мастер депо'

/** Почему действие недоступно: сначала нет прав (rights — права есть, reason — текст), затем status; пусто — доступно. */
const why = (rights: boolean, reason: string, status: string) => (rights ? status : t(reason))

/** Почему действие недоступно в статусе рейса; пусто — доступно. */
function statusReason(trip: TripRow, ...allowed: number[]): string {
  if (allowed.includes(trip.tripStatusType)) return ''
  return t('Недоступно в статусе «{status}»', { status: dictManager.getValue('TripState', trip.tripStatusType) })
}

/** Почему рейс не создать (Ф-РЕЙ-01): создаёт планировщик вагонов; пусто — можно. */
export function createTripReason(): string {
  const u = currentUser.value
  return hasRole(u, RoleType.WAGON_PLANNER) || hasRole(u, RoleType.ADMIN) ? '' : t(PLANNER_REASON)
}

/**
 * Действия с рейсом — общие для панели рейса и меню строки списка рейсов: формирование, правка, отметки отправления
 * и прибытия и их исправление, отцепка и прицепка, отмена. Видны все; недоступное — с причиной. Вызывается в setup.
 *
 * @param changed рейс изменился — список загружается заново
 */
export function useTripActions(changed: () => void) {
  const router = useRouter()
  // исправить или отменить отметку можно 10 минут после неё
  const now = useNow()

  function actionsOf(trip: TripRow): RowAction[] {
    const u = currentUser.value
    const isAdmin = hasRole(u, RoleType.ADMIN)
    // отправление и прибытие отмечают начальник поезда этого рейса и администратор; проверки — на сервере
    const isChief = u?.trip?.id === trip.id && u.trip.crewDutyType === CrewDuty.LNP
    const canMark = isChief || isAdmin
    // редактирует и отменяет рейс планировщик вагонов
    const isPlanner = isAdmin || hasRole(u, RoleType.WAGON_PLANNER)
    // акт составляют начальник поезда этого рейса и мастер депо станции маршрута; станцию проверяет сервер
    const canCouple = canMark || hasRole(u, RoleType.MASTER)
    const fixDeparture = markEditable(trip.departureMarkedDt, now.value)
    const fixArrival = markEditable(trip.arrivalMarkedDt, now.value)
    const status = (...allowed: number[]) => statusReason(trip, ...allowed)
    return [
      { label: 'Формирование', reason: '', run: () => openForm(trip) },
      {
        label: 'Редактировать',
        reason: why(isPlanner, PLANNER_REASON, status(TripStatus.PLANNED)),
        run: () => void edit(trip),
      },
      {
        label: 'Отметить отправление',
        reason: why(canMark, MARK_REASON, status(TripStatus.PLANNED)),
        run: () => void mark(trip, true),
      },
      {
        label: 'Отметить прибытие',
        reason: why(canMark, MARK_REASON, status(TripStatus.IN_PROGRESS)),
        run: () => void mark(trip, false),
      },
      {
        label: 'Исправить отправление',
        reason: why(canMark, MARK_REASON, status(TripStatus.IN_PROGRESS) || (fixDeparture ? '' : t(FIX_REASON))),
        run: () => void mark(trip, true, true),
      },
      {
        label: 'Исправить прибытие',
        reason: why(canMark, MARK_REASON, status(TripStatus.DONE) || (fixArrival ? '' : t(FIX_REASON))),
        run: () => void mark(trip, false, true),
      },
      {
        label: 'Отцепка',
        reason: why(canCouple, COUPLE_REASON, status(TripStatus.IN_PROGRESS)),
        run: () => couple(trip, 'detach'),
      },
      {
        label: 'Прицепка',
        reason: why(canCouple, COUPLE_REASON, status(TripStatus.IN_PROGRESS)),
        run: () => couple(trip, 'attach'),
      },
      {
        label: 'Отменить рейс',
        reason: why(isPlanner, PLANNER_REASON, status(TripStatus.PLANNED)),
        run: () => void cancel(trip),
      },
    ]
  }

  function openForm(trip: TripRow) {
    void router.push({ name: 'trip-form', params: { id: trip.id } })
  }

  function couple(trip: TripRow, kind: string) {
    void router.push({ name: 'trip-coupling', params: { id: trip.id, kind } })
  }

  async function edit(trip: TripRow) {
    const saved = await openDialog<TripRow>(TripNewDialog, {
      trip: { id: trip.id, route: trip.route, plannedDt: trip.plannedDt },
    })
    if (saved) changed()
  }

  async function cancel(trip: TripRow) {
    const done = await openDialog<boolean>(TripCancelDialog, {
      tripId: trip.id,
      tripName: `${trip.trainNumber} • ${trip.name}`,
    })
    if (!done) return
    Notify.create({ message: t('Рейс отменён'), type: 'positive', position: 'bottom' })
    changed()
  }

  // отметить; fix — исправить время уже сделанной отметки или отменить её
  async function mark(trip: TripRow, depart: boolean, fix = false) {
    const markedTime = depart ? trip.departedDt : trip.arrivedDt
    const result = await openDialog<{ crew: TripCrew; message: string }>(TripMarkDialog, {
      tripId: trip.id,
      depart,
      fix: fix ? markedTime : null,
    })
    if (!result) return
    Notify.create({ message: result.message, type: 'positive', position: 'bottom' })
    void loadCurrentUser(true)
    changed()
  }

  return { actionsOf }
}
