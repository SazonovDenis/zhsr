import { date } from 'quasar'
import { computed, reactive, watch } from 'vue'

import type { TrainCard } from '@/api/trains'
import { t } from '@/boot/i18n'
import type { ChoiceItem } from '@/shared/components/choice/choice'
import { dictManager } from '@/shared/dict/DictManager'
import { formatDateTime, isoDateTime } from '@/shared/format'
import { currentComposition, expectedAt } from '@/shared/trains/trainJournal'
import { tripRefDatedText } from '@/shared/trips/tripRefText'
import { tripOptionItem } from '../trips/tripOptionItem'
import type { AttachStop, ChangeChoice, StopGroup } from './attach'

const INPUT_FORMAT = 'YYYY-MM-DDTHH:mm'

/** Исходные значения полей при исправлении плана: время ISO, рейс, остановка, станция, позиция, основания, вагон. */
export interface ChangeInitial {
  time: string
  trip: number | null
  routeStation: number | null
  station: number | null
  seq: number | null
  comment: string
  wagon: number
}

/** Плановое время по умолчанию: at, а если оно не в будущем — следующий полный час. */
function defaultPlanTime(at: string): Date {
  const time = new Date(at)
  if (time.getTime() > Date.now()) return time
  const next = new Date()
  next.setHours(next.getHours() + 1, 0, 0, 0)
  return next
}

/**
 * Как, где и куда прицепить или отцепить — общее для окон «Прицепить вагоны» и «Отцепить вагоны», откуда бы их ни
 * открыли. Место определяется тем, где поезд и вагоны, а не тем, с какой страницы открыто окно:
 * - факт, поезд в рейсе в статусе «выполняется» — на остановке в пути этого рейса (выбор из его станций);
 * - факт, поезд не в рейсе — на станции, где стоят вагоны (station; у поезда без вагонов станцию выбирают);
 * - план — «В рейсе»: рейс поезда в статусе «план» (пусто — вне рейса). В рейсе — станция из остановок рейса и плановое
 *   время в периоде рейса; выбор рейса ставит время на начало рейса, очистка время не меняет; вне рейса — станция и
 *   плановое время.
 * Проверки времени (факт — не в будущем и не раньше последнего факта поезда; план — в будущем и в периоде рейса) —
 * в build(): окно показывает их ошибкой диалога.
 * Прицепка ставит вагоны на позицию: по составу на момент прицепки — текущему по факту или ожидаемому по плану.
 *
 * @param card        поезд: состав для выбора позиции, время последнего факта
 * @param groups      рейсы поезда с остановками (loadStopGroups)
 * @param station     станция вагонов: у прицепки — станция поезда, у отцепки — станция вагонов; null — неизвестна
 * @param factAllowed факт возможен
 * @param attach      прицепка: позиция и выбор станции плана вне рейса; у отцепки — станция вагонов
 * @param at          плановое время по умолчанию, ISO
 * @param trip        рейс, предложенный для плана первым (формирование рейса); null — вне рейса; у прицепки выбранный
 *                    рейс поезда в статусе «план» — окно открывается планом, выбор рейса заблокирован
 * @param plan        открыть сразу планом
 * @param initial     исправление плана: его время, рейс, остановка, станция, позиция и основания; wagon — вагон плана: в
 *                    позициях прицепки его нет (он и есть прицепляемый)
 */
export function useChangeWhere(o: {
  card: TrainCard
  groups: StopGroup[]
  station: number | null
  factAllowed: boolean
  attach: boolean
  at: string
  trip?: number | null
  plan?: boolean
  initial?: ChangeInitial | null
}) {
  const init = o.initial ?? null
  const live = o.groups.find((g) => g.inProgress) ?? null
  // в пути фактом — только на остановке после станции отправления
  const factStops: AttachStop[] = live ? live.stops.slice(1) : []
  // план в рейсе — только у рейсов в статусе «план»; исправляемый план рейса в пути — ещё его рейс
  const planGroups = o.groups.filter((g) => !g.inProgress || g.trip.id === init?.trip)
  const stopText = (s: AttachStop) => `${dictManager.getValue('Station', s.station)} • ${formatDateTime(s.time)}`
  const firstGroup = planGroups.find((g) => g.trip.id === (init ? init.trip : o.trip)) ?? null
  const firstStop = firstGroup?.stops[0] ?? null

  // прицепка из выбранного рейса: сразу планом, рейс не меняется
  const tripLocked = o.attach && !init && firstGroup !== null

  const state = reactive<{
    mode: string
    factStop: number | null
    /** рейс плана; null — вне рейса */
    trip: number | null
    /** остановка рейса плана (route_station.id) */
    stop: number | null
    station: number | null
    factTime: string
    planTime: string | null
    /** позиция прицепки; больше числа вагонов — в конец */
    seq: number
    /** основания, примечания: приказ, причина */
    comment: string
  }>({
    mode: o.factAllowed && !o.plan && !tripLocked ? 'fact' : 'plan',
    factStop: factStops.length > 0 ? 0 : null,
    trip: firstGroup?.trip.id ?? null,
    stop: init?.routeStation ?? firstStop?.routeStation ?? null,
    station: init ? init.station : o.station,
    factTime: date.formatDate(new Date(), INPUT_FORMAT),
    planTime: date.formatDate(init ? new Date(init.time) : (firstStop?.time ?? defaultPlanTime(o.at)), INPUT_FORMAT),
    seq: init?.seq ?? Number.MAX_SAFE_INTEGER,
    comment: init?.comment ?? '',
  })

  const modes = computed<ChoiceItem[]>(() => [
    { id: 'plan', text: 'Планом' },
    { id: 'fact', text: 'Фактом', disabled: !o.factAllowed },
  ])
  const factStopChoices: ChoiceItem[] = factStops.map((s, i) => ({
    id: i,
    text: stopText(s),
    data: { station: dictManager.getValue('Station', s.station), time: formatDateTime(s.time) },
  }))
  // рейс — богатым видом, как в фильтре «Рейс» списка вагонов; выбранный — с плановым отправлением
  const tripChoices: ChoiceItem[] = planGroups.map((g) =>
    g.option ? tripOptionItem(g.option, true) : { id: g.trip.id, text: tripRefDatedText(g.trip) },
  )

  /** Рейс плана; null — вне рейса. */
  const group = computed<StopGroup | null>(() => planGroups.find((g) => g.trip.id === state.trip) ?? null)
  // в списке станция слева, время стоянки справа (data)
  const stopChoices = computed<ChoiceItem[]>(
    () =>
      group.value?.stops.map((s) => ({
        id: s.routeStation,
        text: stopText(s),
        data: { station: dictManager.getValue('Station', s.station), time: formatDateTime(s.time) },
      })) ?? [],
  )

  // смена рейса — первая остановка; смена остановки — время стоянки по расписанию
  watch(
    () => state.trip,
    () => (state.stop = group.value?.stops[0]?.routeStation ?? null),
  )
  watch(
    () => state.stop,
    (id) => {
      const s = group.value?.stops.find((x) => x.routeStation === id)
      if (s) state.planTime = date.formatDate(s.time, INPUT_FORMAT)
    },
  )

  /** Остановка рейса выбранного места; null — вне рейса. */
  const stop = computed<AttachStop | null>(() => {
    if (state.mode === 'fact') return state.factStop === null ? null : (factStops[state.factStop] ?? null)
    return group.value?.stops.find((s) => s.routeStation === state.stop) ?? null
  })
  /** Станция прицепки или отцепки. */
  const targetStation = computed<number | null>(() => stop.value?.station ?? (o.attach ? state.station : o.station))
  /** Момент прицепки или отцепки, ISO: от него считается занятость вагонов и состав. */
  const moment = computed<string>(() => {
    if (state.mode === 'fact') return isoDateTime(new Date(state.factTime))
    return state.planTime ? isoDateTime(new Date(state.planTime)) : o.at
  })
  /** Остановка маршрута рейса (route_station.id) выбранного места; null — вне рейса. */
  const routeStation = computed<number | null>(() => stop.value?.routeStation ?? null)
  /** План в рейсе: плановое время вне периода рейса. */
  const planOutside = computed(() => {
    const g = group.value
    if (state.mode !== 'plan' || !g || !state.planTime) return false
    const at = new Date(state.planTime).getTime()
    return at < new Date(g.from).getTime() || at > new Date(g.to).getTime()
  })

  /** Состав на момент: факт — текущий, план — ожидаемый. */
  const composition = computed(() =>
    (state.mode === 'fact' ? currentComposition(o.card) : expectedAt(o.card, moment.value)).filter(
      (m) => m.wagon.wagon !== init?.wagon,
    ),
  )
  /** Позиции прицепки: «1 — перед 12345» … «в конец». */
  const seqChoices = computed<ChoiceItem[]>(() => [
    ...composition.value.map((w, i) => ({
      id: i + 1,
      text: t('{n} — перед {number}', { n: i + 1, number: w.wagon.number }),
    })),
    { id: Number.MAX_SAFE_INTEGER, text: t('в конец') },
  ])
  // позиция, которой больше нет в составе (сменили вид или время), — снова в конец
  watch(composition, (list) => {
    if (state.seq > list.length) state.seq = Number.MAX_SAFE_INTEGER
  })

  /** Выбор окна; не всё заполнено — текст ошибки. */
  function build(): ChangeChoice | string {
    const where = buildWhere()
    return typeof where === 'string' ? where : { ...where, comment: state.comment.trim() }
  }

  /** Место и время выбора без оснований; не всё заполнено — текст ошибки. */
  function buildWhere(): Omit<ChangeChoice, 'comment'> | string {
    const fact = state.mode === 'fact'
    const seq = o.attach && state.seq <= composition.value.length ? state.seq : null
    if (fact) {
      if (live && !stop.value) return t('Поезд в рейсе: выберите станцию маршрута рейса')
      const at = new Date(state.factTime).getTime()
      if (at > Date.now()) return t('Время не может быть в будущем')
      const last = o.card.lastFactDt
      if (last && at < new Date(last).getTime()) return t('Время не раньше {time}', { time: formatDateTime(last) })
      if (stop.value) return { fact, time: moment.value, station: null, routeStation: stop.value.routeStation, seq }
      if (o.attach && state.station === null) return t('Укажите станцию прицепки')
      return { fact, time: moment.value, station: o.attach ? state.station : null, routeStation: null, seq }
    }
    if (!state.planTime) return t('Укажите плановое время')
    if (new Date(state.planTime).getTime() <= Date.now()) return t('Плановое время должно быть в будущем')
    if (group.value) {
      if (!stop.value) return t('Выберите станцию рейса')
      if (planOutside.value) return t('Плановое время вне периода рейса')
      return { fact, time: moment.value, station: null, routeStation: stop.value.routeStation, seq }
    }
    if (o.attach && state.station === null) return t('Укажите станцию прицепки')
    return { fact, time: moment.value, station: o.attach ? state.station : null, routeStation: null, seq }
  }

  return reactive({
    state,
    live,
    attach: o.attach,
    fixedStation: o.station,
    modes,
    factStopChoices,
    tripChoices,
    tripLocked,
    stopChoices,
    seqChoices,
    targetStation,
    routeStation,
    moment,
    build,
  })
}

export type ChangeWhere = ReturnType<typeof useChangeWhere>
