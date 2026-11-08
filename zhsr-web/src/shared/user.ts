import { Notify } from 'quasar'
import { shallowRef } from 'vue'

import { isNetworkError } from '@/api/http'
import type { CurrentUser } from '@/api/session'
import { fetchCurrentUser, logout } from '@/api/session'
import { t } from '@/boot/i18n'
import { bus } from '@/shared/bus'
import { db, LAST_LOGIN } from '@/shared/db/db'
import { checkServerDb } from '@/shared/db/serverDb'
import { clearCached, loadCached } from '@/shared/offline/cache'
import { resetSessionState } from '@/shared/sessionState'

/**
 * Текущий пользователь для всего приложения: роли, депо, текущий рейс (см. CurrentUser). Загружается один раз
 * (loadCurrentUser); без связи берётся копия, сохранённая на телефоне при прошлой загрузке.
 *
 * Ответ сервера несёт и контекст: каждый такой ответ сверяет локальную БД с серверной (checkServerDb) и версии
 * справочников (событие dict-versions). Пока сверки не было, очередь заявок не отправляется (isServerChecked).
 */
export const currentUser = shallowRef<CurrentUser | null>(null)

let loading: Promise<CurrentUser> | null = null

// последний ответ о пользователе получен от сервера и локальная БД с ним сверена
let checked = false

/** Пользователь: уже загруженный или загрузить. force — загрузить заново (например, после восстановления связи). */
export function loadCurrentUser(force = false): Promise<CurrentUser> {
  if (currentUser.value && !force) return Promise.resolve(currentUser.value)
  loading ??= refreshCurrentUser().finally(() => (loading = null))
  return loading
}

async function refreshCurrentUser(): Promise<CurrentUser> {
  checked = false
  const { data, stale } = await loadCached('user', fetchCurrentUser)
  if (!stale) {
    await checkServerDb(data.dbGuid)
    checked = true
    bus.emit('dict-versions', data.dictVersions)
  }
  currentUser.value = data
  return data
}

/** Сверена ли локальная БД с сервером; сверки ещё не было — выполнить её. false — сервер недоступен. */
export async function isServerChecked(): Promise<boolean> {
  if (!checked) await loadCurrentUser(true)
  return checked
}

/** Сверять локальную БД с сервером при возврате связи. Вызывается один раз при старте приложения (src/boot/ws.ts). */
export function startServerCheck(): void {
  bus.on('connection-back', () => {
    void loadCurrentUser(true).then(() => {
      if (checked) bus.emit('connection-restored')
    })
  })
}

/** Логин последнего вошедшего на этом устройстве: подставляется на странице входа; нет — пусто. */
export async function lastLogin(): Promise<string> {
  return (await db.meta.get(LAST_LOGIN))?.value ?? ''
}

/** Запомнить логин вошедшего. */
export async function rememberLogin(login: string): Promise<void> {
  await db.meta.put({ key: LAST_LOGIN, value: login })
}

/**
 * Забыть пользователя, копии его данных на телефоне и состояние интерфейса в памяти (sessionState): следующий
 * вошедший их не увидит.
 */
export async function forgetUser(): Promise<void> {
  currentUser.value = null
  resetSessionState()
  await clearCached()
}

/**
 * Выйти: завершить сеанс на сервере и забыть пользователя. Возвращает, вышел ли пользователь. Без связи сеанс на
 * сервере остаётся открытым, поэтому выхода нет: пользователь видит причину и остаётся в приложении. То же, пока в
 * очереди на телефоне есть записи: после выхода они ушли бы под другим пользователем.
 */
export async function logoutUser(): Promise<boolean> {
  if ((await db.outbox.count()) > 0) {
    Notify.create({ message: t('Есть неотправленные записи: отправьте или отмените их'), position: 'bottom' })
    return false
  }
  try {
    await logout()
  } catch (e) {
    if (!isNetworkError(e)) throw e
    Notify.create({ message: t('Для выхода нужна связь'), position: 'bottom' })
    return false
  }
  await forgetUser()
  return true
}

/** Есть ли у пользователя роль — id справочника Role (константы RoleType). */
export function hasRole(user: CurrentUser | null, role: number): boolean {
  return !!user?.roles.includes(role)
}

/**
 * ФИО сотрудника для показа: текущий пользователь — с пометкой «(вы)», в том числе в списках выбора. Реактивно:
 * зависит от currentUser.
 */
export function userName(u: { id: number; name: string }): string {
  return u.id === currentUser.value?.id ? t('{name} (вы)', { name: u.name }) : u.name
}
