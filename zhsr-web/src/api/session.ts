// import { http } from './http'

/**
 * API текущего пользователя. Сервер пока не готов: функция отдаёт фиктивные данные, а над ней закомментирован
 * будущий вызов REST.
 */

export interface CurrentUser {
  /** фамилия и инициалы */
  name: string
  /** роль пользователя на русском; переводится при показе */
  role: string
}

/** Войти: проверить логин и пароль и открыть сеанс. Неверные данные — ошибка с текстом для пользователя. */
export async function login(username: string, password: string): Promise<CurrentUser> {
  // return (await http.post<CurrentUser>('/session/login', { username, password })).data
  if (!username || !password) throw new Error('Неверный логин или пароль')
  return fetchCurrentUser()
}

/** Завершить сеанс пользователя на сервере. */
export async function logout(): Promise<void> {
  // await http.post('/session/logout')
  return Promise.resolve()
}

export async function fetchCurrentUser(): Promise<CurrentUser> {
  // return (await http.get<CurrentUser>('/session/user')).data
  return Promise.resolve({ name: 'Жумабаева Г. С.', role: 'Планировщик' })
}
