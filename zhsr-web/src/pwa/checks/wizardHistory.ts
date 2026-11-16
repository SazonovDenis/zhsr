import type { RouteLocationRaw, Router } from 'vue-router'

/**
 * История браузера вокруг мастера начала (CheckStartPage) и заполнения чек-листа и описи: после сохранения и при
 * выходе ни мастер, ни заполнение в истории не остаются — «назад» не возвращает в них.
 */

/** Мастер начала проверки и описи (CheckStartPage). */
const WIZARD_START = 'pwa-check-start'

/** Адрес предыдущей записи истории (vue-router); null — записи нет: страницу открыли первой. */
function backOf(): string | null {
  return (window.history.state?.back as string | null | undefined) ?? null
}

/** Шаг назад по истории и ожидание, когда роутер сменит страницу. */
function stepBack(router: Router): Promise<void> {
  return new Promise((resolve) => {
    const off = router.afterEach(() => {
      off()
      resolve()
    })
    router.back()
  })
}

/** Текущая страница — заполнение, открытое из мастера: шаг назад на мастер. */
async function backToWizard(router: Router): Promise<boolean> {
  const back = backOf()
  if (!back || router.resolve(back).name !== WIZARD_START) return false
  await stepBack(router)
  return true
}

/**
 * Лист снизу (BottomSheet) положил свою запись в историю: закрыть его и дождаться, когда запись снимется. Иначе снятие
 * записи придёт позже перехода и вернёт на страницу, с которой ушли.
 */
export async function closeSheet(close: () => void): Promise<void> {
  if (!window.history.state?.dialog) {
    close()
    return
  }
  const popped = new Promise<void>((resolve) => window.addEventListener('popstate', () => resolve(), { once: true }))
  close()
  await popped
}

/**
 * Сохранено: заменить заполнение (и мастер под ним) на to — просмотр сохранённого. «Назад» с to ведёт туда, откуда
 * открыли мастер или заполнение.
 */
export async function leaveFill(router: Router, to: RouteLocationRaw): Promise<void> {
  await backToWizard(router)
  await router.replace(to)
}

/**
 * Выйти из заполнения (и мастера под ним) без сохранения: на страницу, откуда их открыли; открыли первыми — заменить
 * на fallback.
 */
export async function exitFill(router: Router, fallback: RouteLocationRaw): Promise<void> {
  await backToWizard(router)
  await exitPage(router, fallback)
}

/** Уйти со страницы мастера или заполнения назад; страница открыта первой — заменить её на fallback. */
export async function exitPage(router: Router, fallback: RouteLocationRaw): Promise<void> {
  if (backOf()) await stepBack(router)
  else await router.replace(fallback)
}
