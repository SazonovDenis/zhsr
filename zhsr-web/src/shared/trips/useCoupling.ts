import { date } from 'quasar'
import { computed, ref, shallowRef } from 'vue'

import type { CouplingWagon, TripCrew } from '@/api/trips'
import { coupleWagons, fetchCouplingWagons, fetchTripCrew } from '@/api/trips'
import { t } from '@/boot/i18n'
import type { Busy } from '@/shared/busy'
import type { ChoiceItem } from '@/shared/components/choice/choice'
import { LocationType } from '@/shared/constants/systemDicts'
import { TrainWagonChangeType } from '@/shared/constants/TrainWagonChange'
import { dictManager } from '@/shared/dict/DictManager'
import type { ShownError } from '@/shared/errors'
import { shownError } from '@/shared/errors'
import { isoDateTime } from '@/shared/format'
import { tripRefDatedText } from '@/shared/trips/tripRefText'
import { currentUser, loadCurrentUser } from '@/shared/user'
import { placeText } from '@/shared/wagonPlace'
import type { ResponsibleChoice } from '@/shared/wagons/responsible'
import { responsibleUsrs } from '@/shared/wagons/responsible'

/** Позиция «в конец» в выборе позиции прицепки. */
export const SEQ_END = Number.MAX_SAFE_INTEGER

/**
 * Прицепка или отцепка в рейсе сразу, без плана (Ф-АКТ-01) — общее для экранов десктопа и телефона: рейс,
 * промежуточные остановки, позиция прицепки, основания и примечания (приказ, причина), ответственные за вагоны,
 * список вагонов и оформление. Остановка по умолчанию — станция пользователя, если она среди промежуточных остановок.
 * Прицепка ставит вагоны подряд с выбранной позиции, вагоны с неё сдвигаются назад; отцепка сдвигает вагоны за
 * отцепленным вперёд. Ответственные выбираются обязательно: сотрудники или «не назначен». Проверки — на сервере.
 */
export function useCoupling(tripId: number, attach: boolean, busy: Busy) {
  const type = attach ? TrainWagonChangeType.ATTACH : TrainWagonChangeType.DETACH
  const crew = shallowRef<TripCrew | null>(null)
  const wagons = shallowRef<CouplingWagon[]>([])
  // остановка маршрута — route_station.id: станция может встречаться в маршруте дважды
  const stop = ref<number | null>(null)
  const comment = ref('')
  // значение DatePicker и поля времени телефона; на сервер уходит с поясом
  const time = ref<string | null>(date.formatDate(new Date(), 'YYYY-MM-DDTHH:mm'))
  const selected = ref<string[]>([])
  // позиция первого прицепляемого вагона; SEQ_END — в конец
  const seq = ref<number>(SEQ_END)
  // ответственные за вагоны; null — ещё не выбраны
  const responsible = ref<ResponsibleChoice>(null)
  const error = ref<ShownError | null>(null)
  const running = ref(false)

  /** Промежуточные остановки маршрута: id — route_station.id, текст — станция. */
  const stops = computed(() => {
    const trip = crew.value?.trip
    if (!trip) return []
    return trip.routeStations
      .map((id, i) => ({ id, station: trip.stops[i]!, text: dictManager.getValue('Station', trip.stops[i]) }))
      .slice(1, -1)
  })

  /** Станция выбранной остановки — id справочника Station; не выбрана — null. */
  const station = computed(() => stops.value.find((s) => s.id === stop.value)?.station ?? null)

  /** Вагоны, которые едут в поезде рейса, по позиции: из них позиции прицепки. */
  const riding = computed(() => (crew.value?.wagons ?? []).filter((w) => w.riding).sort((a, b) => a.seq - b.seq))

  /** Позиции прицепки: «1 — перед 12345» … «в конец». */
  const seqChoices = computed<ChoiceItem[]>(() => [
    ...riding.value.map((w, i) => ({ id: i + 1, text: t('{n} — перед {number}', { n: i + 1, number: w.number }) })),
    { id: SEQ_END, text: t('в конец') },
  ])

  const whereText = (w: CouplingWagon) =>
    w.locationType === LocationType.TRIP
      ? t('в поезде')
      : w.busy
        ? `${placeText(w.locationType, w.place)} • ${t('в поезде {train}', { train: w.busy.train.name })}${
            w.busy.trip ? `, ${t('рейс {trip}', { trip: tripRefDatedText(w.busy.trip) })}` : ''
          }`
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
        const own = currentUser.value?.depotStation ?? user.depotStation
        stop.value = stops.value.find((s) => s.station === own)?.id ?? null
        await loadWagons()
      })
    } catch (e) {
      error.value = shownError(e)
    }
  }

  /** Сменить остановку (route_station.id): для прицепки список вагонов — станции остановки. */
  async function setStop(id: number | null) {
    stop.value = id
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
      !!stop.value &&
      !!time.value &&
      responsible.value !== null &&
      chosen.value.length > 0 &&
      chosen.value.every((w) => w.available) &&
      !running.value,
  )

  /** Вагон стоит в депо или парке отстоя: прицепить нельзя, можно вернуть на станцию актом приёма-передачи. */
  const returnable = (w: CouplingWagon) =>
    attach && !w.busy && (w.locationType === LocationType.DEPOT || w.locationType === LocationType.YARD)

  /** Все отмеченные вагоны стоят в одном депо или парке отстоя: откуда вернуть их на станцию; иначе null. */
  const returnFrom = computed(() => {
    const first = chosen.value[0]
    if (!first || !station.value || !chosen.value.every(returnable)) return null
    if (chosen.value.some((w) => w.locationType !== first.locationType || w.place !== first.place)) return null
    return first.locationType === LocationType.DEPOT ? { depot: first.place } : { yard: first.place }
  })

  /** Прицепить или отцепить. @return true — оформлено */
  async function submit(): Promise<boolean> {
    if (!stop.value) return false
    error.value = null
    running.value = true
    try {
      await coupleWagons(tripId, {
        changeType: type,
        routeStation: stop.value,
        comment: comment.value,
        // отмеченные — в порядке списка: так они и встанут в поезд подряд
        wagons: wagons.value.filter((w) => selected.value.includes(w.number)).map((w) => w.number),
        time: isoDateTime(new Date(time.value!)),
        seq: attach && seq.value <= riding.value.length ? seq.value : null,
        responsibleUsrs: responsibleUsrs(responsible.value),
      })
      return true
    } catch (e) {
      error.value = shownError(e)
      return false
    } finally {
      running.value = false
    }
  }

  /**
   * Прицепить или отцепить с ответственными у каждого вагона своими: вагоны с одинаковыми ответственными — одним
   * запросом, группы — по порядку списка; при прицепке следующая группа встаёт за предыдущей. Пусто — ответственный не
   * назначен. Ошибка группы останавливает отправку: уже оформленные группы остаются оформленными.
   *
   * @param usrsOf ответственные вагона по номеру
   * @return true — оформлено всё
   */
  async function submitEach(usrsOf: (number: string) => number[]): Promise<boolean> {
    if (!stop.value) return false
    const list = wagons.value.filter((w) => selected.value.includes(w.number)).map((w) => w.number)
    const groups = new Map<string, { usrs: number[]; wagons: string[] }>()
    for (const n of list) {
      const usrs = [...usrsOf(n)].sort((a, b) => a - b)
      const key = usrs.join(',')
      if (!groups.has(key)) groups.set(key, { usrs, wagons: [] })
      groups.get(key)!.wagons.push(n)
    }
    error.value = null
    running.value = true
    try {
      let at = attach && seq.value <= riding.value.length ? seq.value : null
      for (const g of groups.values()) {
        await coupleWagons(tripId, {
          changeType: type,
          routeStation: stop.value,
          comment: comment.value,
          wagons: g.wagons,
          time: isoDateTime(new Date(time.value!)),
          seq: at,
          responsibleUsrs: g.usrs,
        })
        if (at !== null) at += g.wagons.length
      }
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
    stop,
    station,
    comment,
    time,
    selected,
    seq,
    seqChoices,
    responsible,
    error,
    running,
    stops,
    whereText,
    load,
    setStop,
    canSubmit,
    submit,
    submitEach,
    returnable,
    returnFrom,
  }
}
