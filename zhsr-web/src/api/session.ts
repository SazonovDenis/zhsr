import { isMobile } from '@/shared/device'
import { fakeTime } from './fakeTime'
import { fakeNetwork } from './http'
// import { http } from './http'

/**
 * API текущего пользователя. Сервер пока не готов: функции отдают фиктивные данные, а над ними закомментирован
 * будущий вызов REST.
 *
 * Фиктивный пользователь выбирается логином при входе (см. FAKE_USERS): lnp — начальник поезда, cond — проводник,
 * pem — ПЭМ, master — мастер депо, instr — инструктор поездных бригад, plan — планировщик. Без входа — начальник
 * поезда на телефоне и планировщик с ролью мастера депо на компьютере.
 */

/** Текущий рейс пользователя: рейс, в бригаде которого он состоит сейчас. */
export interface UserTrip {
  /** id рейса — для запросов */
  id: string
  /** номер поезда */
  train: string
  /** маршрут рейса — для показа: «Алматы — Астана» */
  name: string
  /** состояние рейса — id справочника TripState */
  state: string
  /** отправление: фактическое, если было, иначе плановое; ISO */
  departure: string
  /** плановое прибытие, ISO */
  arrival: string
  /** должность пользователя в бригаде рейса: LNP, PEM или CONDUCTOR */
  duty: string
  /** вагоны, за которыми закреплён проводник; у остальных должностей пусто */
  wagons: string[]
}

export interface CurrentUser {
  id: string
  /** фамилия и инициалы */
  name: string
  /** имя — для приветствия */
  firstName: string
  /** роли — id справочника Role; роли совмещаются */
  roles: string[]
  /** службы ролей пользователя — id справочника Service */
  services: string[]
  /** депо, в котором работает сотрудник, — id справочника Depot */
  depot: string | null
  /** станция этого депо — id справочника Station */
  depotStation: string | null
  /** текущий рейс; null — пользователь сейчас не в бригаде рейса */
  trip: UserTrip | null
}

/** Войти: проверить логин и пароль и открыть сеанс. Неверные данные — ошибка с текстом для пользователя. */
export async function login(username: string, password: string): Promise<CurrentUser> {
  // return (await http.post<CurrentUser>('/session/login', { username, password })).data
  if (!username || !password) throw new Error('Неверный логин или пароль')
  fakeUserKey = username in FAKE_USERS ? username : null
  return fetchCurrentUser()
}

/** Завершить сеанс пользователя на сервере. */
export async function logout(): Promise<void> {
  // await http.post('/session/logout')
  fakeUserKey = null
  return Promise.resolve()
}

/** Текущий пользователь: роли, депо, текущий рейс. */
export async function fetchCurrentUser(): Promise<CurrentUser> {
  // return (await http.get<CurrentUser>('/getUserInfo')).data
  fakeNetwork()
  return Promise.resolve(fakeCurrentUser())
}

// --- фиктивные данные ---

let fakeUserKey: string | null = null

/** Фиктивный текущий пользователь; нужен и другим заготовкам API — например, чтобы отобрать «мои» заявки. */
export function fakeCurrentUser(): CurrentUser {
  return FAKE_USERS[fakeUserKey ?? (isMobile.value ? 'lnp' : 'plan')]!
}

/** Рейс, в котором едет бригада фиктивных пользователей: отправился 10 часов назад. */
export const FAKE_TRIP = { id: 'T2', train: '002', name: 'Алматы — Астана', state: 'IN_PROGRESS' }

function trip(duty: string, wagons: string[] = []): UserTrip {
  return { ...FAKE_TRIP, departure: fakeTime(-10), arrival: fakeTime(9), duty, wagons }
}

const FAKE_USERS: Record<string, CurrentUser> = {
  lnp: {
    id: 'u3',
    name: 'Ахметов Б. К.',
    firstName: 'Болат',
    roles: ['LNP', 'CONDUCTOR'],
    services: [],
    depot: 'D2',
    depotStation: 'ALA',
    trip: trip('LNP'),
  },
  cond: {
    id: 'u16',
    name: 'Абенова Д. С.',
    firstName: 'Динара',
    roles: ['CONDUCTOR'],
    services: [],
    depot: 'D2',
    depotStation: 'ALA',
    trip: trip('CONDUCTOR', ['17043']),
  },
  pem: {
    id: 'u7',
    name: 'Касымов Д. Е.',
    firstName: 'Данияр',
    roles: ['PEM'],
    services: ['TECHNICAL'],
    depot: 'D2',
    depotStation: 'ALA',
    trip: trip('PEM'),
  },
  master: {
    id: 'u4',
    name: 'Оспанова М. Т.',
    firstName: 'Мадина',
    roles: ['MASTER'],
    services: ['TECHNICAL'],
    depot: 'D1',
    depotStation: 'AST',
    trip: null,
  },
  instr: {
    id: 'u10',
    name: 'Сейткали Р. А.',
    firstName: 'Руслан',
    roles: ['INSTRUCTOR'],
    services: ['INSTRUCTOR'],
    depot: 'D1',
    depotStation: 'AST',
    trip: null,
  },
  plan: {
    id: 'u2',
    name: 'Жумабаева Г. С.',
    firstName: 'Гульнара',
    roles: ['PLANNER', 'MASTER'],
    services: ['TECHNICAL'],
    depot: 'D1',
    depotStation: 'AST',
    trip: null,
  },
}
