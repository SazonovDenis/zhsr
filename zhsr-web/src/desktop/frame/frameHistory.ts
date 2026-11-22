import type { Router } from 'vue-router'

/**
 * История фреймов: какой маршрут стоит в каждой записи истории браузера.
 *
 * Внутри фрейма пользователь переключает параметры, и каждое переключение может лечь в историю отдельной записью того
 * же маршрута (например, /trains/2?trip=127, затем /trains/2?trip=128). Выход из фрейма пропускает такие записи и
 * возвращает на ближайшую запись другого маршрута — туда, откуда пользователь открыл фрейм.
 *
 * Номер записи берётся из состояния vue-router (history.state.position); имя маршрута запоминается после каждого
 * перехода. Записи до обновления страницы неизвестны: о предыдущей записи известен только адрес (history.state.back),
 * о более ранних — ничего.
 */
const names = new Map<number, string>()

/** Запоминать имя маршрута каждой записи истории. */
export function trackFrameHistory(router: Router): void {
  router.afterEach((to, _from, failure) => {
    if (failure) return
    const position: unknown = window.history.state?.position
    if (typeof position === 'number' && typeof to.name === 'string') names.set(position, to.name)
  })
}

/**
 * На сколько записей назад стоит ближайший другой маршрут; 0 — такой записи в известной истории нет.
 */
export function frameBackSteps(router: Router): number {
  const state = window.history.state as { position?: unknown; back?: unknown } | null
  const current = state?.position
  if (typeof current !== 'number') return 0
  const name = router.currentRoute.value.name
  for (let position = current - 1; position >= 0; position--) {
    let prev = names.get(position)
    if (prev === undefined && position === current - 1 && typeof state?.back === 'string') {
      prev = router.resolve(state.back).name as string | undefined
    }
    if (prev === undefined) return 0
    if (prev !== name) return current - position
  }
  return 0
}
