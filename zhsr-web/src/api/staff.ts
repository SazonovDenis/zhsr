import type { TableSort } from '@/shared/components/table/types'
import type { WagonRef } from './requests'
import type { TripRef } from './wagons'
import { http } from './http'

/**
 * API сотрудников: список (GET /staff), перемещение в другое депо (POST /staff/{id}/depot), смена категории
 * проводника (POST /staff/{id}/category). Доступно планировщикам и администратору.
 */

/** Фильтр списка сотрудников. */
export interface StaffFilter {
  /** часть ФИО, логина или телефона */
  search: string
  /** станция текущего депо сотрудника — id справочника Station */
  station: number | null
  /** рейс: сотрудник в бригаде этого рейса */
  trip: number | null
  /** поезд: сотрудник в бригаде рейса этого поезда в статусе «план» или «выполняется» */
  train: number | null
}

/** Сотрудник в списке: роли, текущие депо и категория, где сейчас (факт) и куда назначен (план). */
export interface StaffRow {
  id: number
  fullName: string
  /** логин; виден только администратору, остальным — null */
  login: string | null
  disabled: boolean
  /** роли — id справочника Role */
  roles: number[]
  /** текущее депо — id справочника Depot; null — нет */
  depot: number | null
  /** станция текущего депо — id справочника Station; null — нет */
  station: number | null
  /** категория проводника — id справочника ConductorCategory; null — нет */
  categoryType: number | null
  /** выполняющийся рейс, в бригаде которого сотрудник; null — не в рейсе */
  factTrip: TripRef | null
  /** должность в рейсе factTrip — id справочника CrewDuty; null — не в рейсе */
  factDuty: number | null
  /** вагоны, за которые сотрудник отвечает сейчас */
  factWagons: WagonRef[]
  /** ближайший плановый рейс, в бригаде которого сотрудник; null — не назначен */
  planTrip: TripRef | null
  /** должность в рейсе planTrip — id справочника CrewDuty; null — не назначен */
  planDuty: number | null
  /** вагоны рейса planTrip по плану ответственных */
  planWagons: WagonRef[]
}

export async function fetchStaff(filter: StaffFilter, sort: TableSort | null): Promise<StaffRow[]> {
  return (await http.get<StaffRow[]>('/staff', { params: { ...filter, sort: sort?.field, desc: sort?.desc } })).data
}

/** Переместить сотрудника в депо depot со времени time (ISO с поясом; null — сейчас). Ответ — сотрудник после. */
export async function moveStaffDepot(id: number, depot: number, time: string | null): Promise<StaffRow> {
  return (await http.post<StaffRow>(`/staff/${id}/depot`, { value: depot, time })).data
}

/** Сменить категорию проводника со времени time (ISO с поясом; null — сейчас). Ответ — сотрудник после. */
export async function changeStaffCategory(id: number, category: number, time: string | null): Promise<StaffRow> {
  return (await http.post<StaffRow>(`/staff/${id}/category`, { value: category, time })).data
}
