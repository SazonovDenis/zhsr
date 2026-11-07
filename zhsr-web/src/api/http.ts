import type { AxiosError } from 'axios'
import axios from 'axios'

// HTTP-клиент сервера zhsr-app; в dev-режиме /api проксируется (quasar.config.ts > devServer.proxy)
// списки в параметрах — повтором имени, как их разбирает Spring: states=1&states=2
export const http = axios.create({ baseURL: '/api', paramsSerializer: { indexes: null } })

/**
 * Ошибка запроса к серверу: статус (0 — ответа нет), текст для пользователя из {"message": "..."} и запрос, который
 * упал, — «GET /api/wagons?number=12»: его показывают второй строкой под текстом ошибки.
 */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    message: string,
    readonly request: string,
  ) {
    super(message)
  }
}

/** Запрос axios для показа: метод и адрес с параметрами. */
function requestOf(e: AxiosError): string {
  const config = e.config
  if (!config) return ''
  return `${(config.method ?? 'get').toUpperCase()} ${http.getUri(config)}`
}

/** Сервер требует сменить пароль (406): разрешена только смена пароля. */
export function isPasswordChangeRequired(e: unknown): boolean {
  return e instanceof ApiError && e.status === 406
}

/** Нет входа (401): сеанс не открыт или истёк. */
export function isLoginRequired(e: unknown): boolean {
  return e instanceof ApiError && e.status === 401
}

let passwordChangeHandler: (() => void) | null = null
let loginHandler: (() => void) | null = null

/** Что делать, когда сервер требует сменить пароль; задаёт роутер — переход на страницу смены пароля. */
export function onPasswordChangeRequired(handler: () => void): void {
  passwordChangeHandler = handler
}

/** Что делать, когда нет входа; задаёт роутер — переход на страницу входа. */
export function onLoginRequired(handler: () => void): void {
  loginHandler = handler
}

// запрос входа: его 401 — неверный логин или пароль, это ошибка формы входа, а не потеря сеанса
const LOGIN_URL = '/session/login'

// ответ с ошибкой — ApiError с текстом сервера и запросом; 406 — переход на смену пароля, 401 (кроме запроса входа) —
// переход на страницу входа. Нет ответа (нет связи) —
// ApiError со статусом 0: её распознаёт isNetworkError
http.interceptors.response.use(undefined, (e: unknown) => {
  if (!axios.isAxiosError(e)) return Promise.reject(e instanceof Error ? e : new Error(String(e)))
  if (!e.response) return Promise.reject(new ApiError(0, 'Нет связи', requestOf(e)))
  const data = e.response.data as { message?: unknown } | undefined
  const message =
    typeof data?.message === 'string' ? data.message : `Ошибка сервера: ${e.response.status} ${e.response.statusText}`
  const error = new ApiError(e.response.status, message, requestOf(e))
  if (isPasswordChangeRequired(error)) passwordChangeHandler?.()
  if (isLoginRequired(error) && e.config?.url !== LOGIN_URL) loginHandler?.()
  return Promise.reject(error)
})

/**
 * Сервер ответил по существу и отказал (400, 403, 404 и другие 4xx): повтор даст то же самое. Не отказ — сбой сервера
 * (5xx), нет входа (401), нужна смена пароля (406), тайм-аут и перегрузка (408, 429): это проходит, запрос надо
 * повторить.
 */
export function isRefusal(e: unknown): boolean {
  if (!(e instanceof ApiError) || e.status < 400 || e.status >= 500) return false
  return ![401, 406, 408, 429].includes(e.status)
}

/** Ошибка связи, а не ответ сервера: мобильная часть по ней берёт данные из копии на телефоне или ставит ввод в очередь. */
export function isNetworkError(e: unknown): boolean {
  return (e instanceof ApiError && e.status === 0) || (axios.isAxiosError(e) && !e.response)
}
