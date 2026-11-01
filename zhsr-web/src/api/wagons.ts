import type { TableSort } from '@/shared/components/table/types'
import { fakeSort } from './fakeSort'
// import { http } from './http'

/**
 * API вагонов. Сервер пока не готов: функции отдают фиктивные данные, а над ними закомментирован будущий вызов
 * REST.
 */

/** Расположение вагона: где он сейчас и с какого времени. */
export interface WagonLocation {
  /** вид расположения — id справочника LocationKind */
  kind: string
  /** название места: станция, депо, рейс, парк отстоя */
  name: string
  /** с какого времени, ISO */
  since: string
}

export interface Wagon {
  id: string
  number: string
  /** тип вагона — id справочника WagonType */
  type: string
  /** модель — id справочника WagonModel */
  model: string
  /** депо приписки — id справочника Depot */
  depot: string
  location: WagonLocation
  /** состояние вагона — id справочника WagonState */
  state: string
  /** с какого времени вагон в этом состоянии, ISO */
  stateSince: string
}

export interface WagonFilter {
  /** поиск по номеру вагона */
  number: string
  /** станция — id справочника Station */
  station: string | null
  /** рейс — id рейса (api/trips.ts) */
  trip: string | null
  /** виды расположения — id справочника LocationKind */
  locationKinds: string[]
  /** состояния вагона — id справочника WagonState */
  states: string[]
}

/** Новый вагон: паспорт, депо приписки, начальное расположение и состояние. */
export interface WagonDraft {
  number: string
  /** тип вагона — id справочника WagonType */
  type: string
  /** модель — id справочника WagonModel */
  /** модель — id справочника WagonModel */
  model: string
  year: string
  maker: string
  /** депо приписки — id справочника Depot */
  depot: string
  /** вид начального расположения — id справочника LocationKind: DEPOT, YARD или STATION */
  locationKind: string
  /** место: id справочника Depot, Yard или Station — по виду расположения */
  locationRef: string
  /** начальное состояние — id справочника WagonState */
  state: string
  /** ввод в эксплуатацию, ISO; null — ещё не задан */
  since: string | null
}

/**
 * Список вагонов по фильтру. sort — поле и направление сортировки; null — порядок сервера по умолчанию.
 */
export async function fetchWagons(filter: WagonFilter, sort: TableSort | null): Promise<Wagon[]> {
  // return (await http.get<Wagon[]>('/wagons', { params: { ...filter, sort: sort?.field, desc: sort?.desc } })).data
  return Promise.resolve(
    fakeSort(
      FAKE_WAGONS.filter(
        (w) =>
          w.number.includes(filter.number) &&
          (filter.locationKinds.length === 0 || filter.locationKinds.includes(w.location.kind)) &&
          (filter.states.length === 0 || filter.states.includes(w.state)),
      ),
      sort,
    ),
  )
}

export async function createWagon(draft: WagonDraft): Promise<Wagon> {
  // return (await http.post<Wagon>('/wagons', draft)).data
  const wagon: Wagon = {
    id: draft.number,
    number: draft.number,
    type: draft.type,
    model: draft.model,
    depot: draft.depot,
    location: { kind: draft.locationKind, name: draft.locationRef, since: draft.since ?? '' },
    state: draft.state,
    stateSince: draft.since ?? '',
  }
  FAKE_WAGONS.push(wagon)
  return Promise.resolve(wagon)
}

// --- фиктивные данные: значения из макета ---

const FAKE_WAGONS: Wagon[] = [
  {
    id: '15021',
    number: '15021',
    type: 'COMPARTMENT',
    model: 'TULPAR_TALGO',
    depot: 'D1',
    location: { kind: 'DEPOT', name: 'Вагонное депо Астана', since: '2026-10-27T09:10' },
    state: 'IN_IDLE',
    stateSince: '2026-10-27T09:10',
  },
  {
    id: '15022',
    number: '15022',
    type: 'PLATZKART',
    model: 'TULPAR_TMH',
    depot: 'D1',
    location: { kind: 'TRIP', name: 'Рейс 001 Астана — Алматы', since: '2026-10-28T21:00' },
    state: 'IN_TRIP',
    stateSince: '2026-10-28T21:00',
  },
  {
    id: '15023',
    number: '15023',
    type: 'SLEEPER',
    model: 'STADLER_SLEEPER',
    depot: 'D2',
    location: { kind: 'DEPOT', name: 'Вагонное депо Алматы', since: '2026-10-20T08:00' },
    state: 'IN_REPAIR',
    stateSince: '2026-10-20T08:00',
  },
  {
    id: '15024',
    number: '15024',
    type: 'RESTAURANT',
    model: 'TULPAR_TALGO',
    depot: 'D4',
    location: { kind: 'YARD', name: 'Караганды-Сортировочная', since: '2026-10-15T12:30' },
    state: 'RESERVE',
    stateSince: '2026-10-15T12:30',
  },
  {
    id: '15025',
    number: '15025',
    type: 'STAFF',
    model: 'STADLER_STAFF',
    depot: 'D2',
    location: { kind: 'STATION', name: 'Алматы-2', since: '2026-09-30T10:00' },
    state: 'OUT_OF_SERVICE',
    stateSince: '2026-09-30T10:00',
  },
]
