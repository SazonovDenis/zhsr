// import { http } from './http'

/**
 * API текущего состояния: сводка для пользователя, например сколько актов передачи ждут его приёма. Сервер пока не
 * готов: функция отдаёт фиктивные данные, а над ней закомментирован будущий вызов REST.
 */

export interface CurrentState {
  /**
   * Счётчики пунктов левого меню: id пункта из menu.json → число. Пункта нет или 0 — счётчик не показывается.
   * Например, requests — заявки, ожидающие обработки; acts — акты передачи, ожидающие приёма текущим пользователем.
   */
  counters: Record<string, number>
}

export async function fetchCurrentState(): Promise<CurrentState> {
  // return (await http.get<CurrentState>('/session/state')).data
  return Promise.resolve({ counters: { requests: 7, acts: 2 } })
}
