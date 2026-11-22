import type { DictName } from '@/shared/dict/dicts'
import { http } from './http'

/**
 * API текущего пользователя: вход, выход, смена пароля и кто вошёл (сервер zhsr-app: SessionController).
 */

/** Текущий рейс пользователя: рейс, в бригаде которого он состоит сейчас. */
export interface UserTrip {
  /** id рейса — для запросов */
  id: number
  /** номер поезда */
  trainNumber: string
  /** маршрут рейса — для показа: «Алматы — Астана» */
  name: string
  /** состояние рейса — id справочника TripState (константы TripStatus) */
  tripStatusType: number
  /** отправление: фактическое, если было, иначе плановое; ISO с часовым поясом */
  departure: string
  /** плановое прибытие, ISO с часовым поясом */
  arrival: string
  /** должность пользователя в бригаде рейса — id справочника CrewDuty (константы CrewDuty) */
  crewDutyType: number
  /** номера вагонов, за которыми закреплён проводник; у остальных должностей пусто */
  wagons: string[]
  /** плановое отправление, ISO с часовым поясом */
  plannedDt: string
  /** фактическое отправление, ISO с часовым поясом; null — рейс не отправлен */
  departedDt: string | null
  /** фактическое прибытие, ISO с часовым поясом; null — рейс не прибыл */
  arrivedDt: string | null
  /** когда отмечено отправление, ISO: 10 минут после этого отметку можно исправить или отменить; null — не отмечено */
  departureMarkedDt: string | null
  /** когда отмечено прибытие, ISO; null — не отмечено */
  arrivalMarkedDt: string | null
}

export interface CurrentUser {
  id: number
  /** фамилия и инициалы */
  name: string
  /** имя — для приветствия */
  firstName: string
  /** роли — id справочника Role (константы RoleType); роли совмещаются */
  roles: number[]
  /** службы ролей пользователя — id справочника Service (константы ServiceType) */
  services: number[]
  /** депо, в котором работает сотрудник, — id справочника Depot */
  depot: number | null
  /** станция этого депо — id справочника Station */
  depotStation: number | null
  /** текущий рейс; null — пользователь сейчас не в бригаде рейса */
  trip: UserTrip | null
  /** соседний рейс: предыдущий или следующий относительно текущего, показывается по ссылке; null — нет */
  otherTrip: UserTrip | null
  /** идентификатор серверной БД: с ним сверяется локальная БД браузера */
  dbGuid: string
  /** текущие версии справочников */
  dictVersions: Partial<Record<DictName, string>>
}

/**
 * Войти: проверить логин и пароль и открыть сеанс. Неверные данные — ошибка с текстом для пользователя. Пароль задан
 * администратором — ответ 406 (isPasswordChangeRequired): сеанс открыт, нужно сменить пароль.
 */
export async function login(username: string, password: string): Promise<void> {
  await http.post('/session/login', { username, password })
}

/**
 * Сменить пароль вошедшему пользователю. Неверный текущий пароль, короткий новый — ошибка с текстом для пользователя.
 */
export async function changePassword(oldPassword: string, newPassword: string): Promise<void> {
  await http.post('/session/password', { oldPassword, newPassword })
}

/** Завершить сеанс пользователя на сервере. */
export async function logout(): Promise<void> {
  await http.post('/session/logout')
}

/** Текущий пользователь: роли, депо, текущий рейс; и контекст сервера: идентификатор БД, версии справочников. */
/** БД сервера загружена с демо-данными (db_info.demo): страница входа показывает быстрый вход. Доступно без входа. */
export async function fetchDemo(): Promise<boolean> {
  return (await http.get<boolean>('/session/demo')).data
}

export async function fetchCurrentUser(): Promise<CurrentUser> {
  return (await http.get<CurrentUser>('/getUserInfo')).data
}

/** Пароль демо-пользователей: db-data/demo/03-usr.tsv; admin — db-data/demo/04-usr-admin.sql. */
const DEMO_PASSWORD = 'qwerty-123456-QAZWSX-!@#$%^'

/** Демо-пользователи для быстрого входа при dev-запуске (см. LoginPage). */
export const DEMO_LOGINS = [
  { login: 'plan', name: 'Жумабаева Г. С.', password: DEMO_PASSWORD },
  { login: 'lnp', name: 'Ахметов Б. К.', password: DEMO_PASSWORD },
  { login: 'cond', name: 'Абенова Д. С.', password: DEMO_PASSWORD },
  { login: 'pem', name: 'Касымов Д. Е.', password: DEMO_PASSWORD },
  { login: 'master', name: 'Оспанова М. Т.', password: DEMO_PASSWORD },
  { login: 'recv', name: 'Омарова С. К.', password: DEMO_PASSWORD },
  { login: 'instr', name: 'Сейткали Р. А.', password: DEMO_PASSWORD },
  { login: 'medic', name: 'Нурланова А. Б.', password: DEMO_PASSWORD },
  { login: 'admin', name: 'Администратор', password: DEMO_PASSWORD },
]
