import { date } from 'quasar'
import { computed, ref, shallowRef } from 'vue'

import type { CouplingWagon, TripCrew } from '@/api/trips'
import { coupleWagons, fetchCouplingWagons, fetchTripCrew } from '@/api/trips'
import { t } from '@/boot/i18n'
import type { Busy } from '@/shared/busy'
import { CouplingType, LocationType } from '@/shared/constants/systemDicts'
import { dictManager } from '@/shared/dict/DictManager'
import type { ShownError } from '@/shared/errors'
import { shownError } from '@/shared/errors'
import { isoDateTime } from '@/shared/format'
import { currentUser, loadCurrentUser } from '@/shared/user'
import { placeText } from '@/shared/wagonPlace'

/**
 * Акт отцепки или прицепки (Ф-АКТ-01) — общее для экранов десктопа и телефона: рейс, промежуточные остановки, приказ,
 * причина, список вагонов и проведение акта. Станция по умолчанию — станция пользователя, если она среди промежуточных
 * остановок. Проверки — на сервере.
 */
export function useCoupling(tripId: number, attach: boolean, busy: Busy) {
  const type = attach ? CouplingType.ATTACH : CouplingType.DETACH
  const crew = shallowRef<TripCrew | null>(null)
  const wagons = shallowRef<CouplingWagon[]>([])
  const station = ref<number | null>(null)
  const order = ref('')
  const reason = ref('')
  // значение DatePicker и поля времени телефона; на сервер уходит с поясом
  const time = ref<string | null>(date.formatDate(new Date(), 'YYYY-MM-DDTHH:mm'))
  const selected = ref<string[]>([])
  const error = ref<ShownError | null>(null)
  const running = ref(false)

  const stops = computed(() => {
    const list = crew.value?.trip.stops ?? []
    return list.slice(1, -1).map((id) => ({ id, text: dictManager.getValue('Station', id) }))
  })

  const whereText = (w: CouplingWagon) =>
    w.locationType === LocationType.TRIP
      ? t('в составе')
      : w.otherTrain
        ? `${placeText(w.locationType, w.place)} • ${t('в составе рейса {trip}', { trip: w.otherTrain })}`
        : w.available
          ? placeText(w.locationType, w.place)
          : `${placeText(w.locationType, w.place)} • ${t('сначала передача на станцию')}`

  async function loadWagons() {
    selected.value = []
    wagons.value = attach && !station.value ? [] : await fetchCouplingWagons(tripId, type, station.value)
  }

  /** Загрузить рейс и вагоны; ошибка — в error. */
  async function load() {
    error.value = null
    try {
      await busy.run(async () => {
        const [data, user] = await Promise.all([
          fetchTripCrew(tripId),
          loadCurrentUser(),
          dictManager.prepareDicts('Station', 'Depot', 'Yard', 'WagonType'),
        ])
        crew.value = data
        const inner = data.trip.stops.slice(1, -1)
        const own = currentUser.value?.depotStation ?? user.depotStation
        station.value = own !== null && inner.includes(own) ? own : null
        await loadWagons()
      })
    } catch (e) {
      error.value = shownError(e)
    }
  }

  /** Сменить станцию: для прицепки список вагонов — этой станции. */
  async function setStation(id: number | null) {
    station.value = id
    if (!attach) return
    error.value = null
    try {
      await busy.run(loadWagons())
    } catch (e) {
      error.value = shownError(e)
    }
  }

  const chosen = computed(() => wagons.value.filter((w) => selected.value.includes(w.number)))
  const canSubmit = computed(
    () =>
      !!station.value &&
      !!time.value &&
      chosen.value.length > 0 &&
      chosen.value.every((w) => w.available) &&
      !running.value,
  )

  /** Вагон стоит в депо или парке отстоя: прицепить нельзя, можно вернуть на станцию актом приёма-передачи. */
  const returnable = (w: CouplingWagon) =>
    attach && !w.otherTrain && (w.locationType === LocationType.DEPOT || w.locationType === LocationType.YARD)

  /** Все отмеченные вагоны стоят в одном депо или парке отстоя: откуда вернуть их на станцию; иначе null. */
  const returnFrom = computed(() => {
    const first = chosen.value[0]
    if (!first || !station.value || !chosen.value.every(returnable)) return null
    if (chosen.value.some((w) => w.locationType !== first.locationType || w.place !== first.place)) return null
    return first.locationType === LocationType.DEPOT ? { depot: first.place } : { yard: first.place }
  })

  /** Провести акт. @return true — проведён */
  async function submit(): Promise<boolean> {
    if (!station.value) return false
    error.value = null
    running.value = true
    try {
      await coupleWagons(tripId, {
        couplingType: type,
        station: station.value,
        orderInfo: order.value,
        reason: reason.value,
        wagons: selected.value,
        actDt: isoDateTime(new Date(time.value!)),
      })
      return true
    } catch (e) {
      error.value = shownError(e)
      return false
    } finally {
      running.value = false
    }
  }

  return {
    crew,
    wagons,
    station,
    order,
    reason,
    time,
    selected,
    error,
    running,
    stops,
    whereText,
    load,
    setStation,
    canSubmit,
    submit,
    returnable,
    returnFrom,
  }
}
