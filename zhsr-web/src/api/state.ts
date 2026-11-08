import { http } from './http'

/** API текущего состояния: сводка для пользователя, например сколько актов передачи ждут его приёма. */

export interface CurrentState {
  /**
   * Счётчики пунктов левого меню: id пункта из menu.json → число. Пункта нет или 0 — счётчик не показывается.
   * requests — заявки, которые ждут действия пользователя: назначены ему, в работе у него, выполнены и ждут его
   * приёма; acts — акты передачи, которые ждут его приёма.
   */
  counters: Record<string, number>
}

export async function fetchCurrentState(): Promise<CurrentState> {
  return (await http.get<CurrentState>('/session/state')).data
}
