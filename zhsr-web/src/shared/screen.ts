import { Screen } from 'quasar'

/**
 * Размер экрана корзинами: 0 — телефон (уже 30em), 6 — широкий монитор (от 93.75em = 1500px).
 * Компоненты решают по корзине, а не по пикселям: пороги заданы в одном месте.
 */
export function screenScale(): number {
  return scaleOf(Screen.width / 16)
}

/**
 * Масштаб по меньшей стороне экрана. Нужен там, где решает не ширина, а остаётся ли вокруг места вообще: телефон
 * лёжа — это 844x390, по ширине он не мелкий, а развернуться негде.
 */
export function screenScaleMin(): number {
  return scaleOf(Math.min(Screen.width, Screen.height) / 16)
}

/** Экран лежит: ширина больше высоты. */
export function isLandscape(): boolean {
  return Screen.width > Screen.height
}

/** Верхняя граница нулевой корзины в пикселях — для свойств Quasar, которые принимают только пиксели (breakpoint). */
export const SCALE0_MAX_PX = 30 * 16

/** Корзина для ширины в em (1em = 16px). */
function scaleOf(em: number): number {
  if (em < 30) return 0
  if (em < 44.375) return 1
  if (em < 59.375) return 2
  if (em < 68.75) return 3
  if (em < 81.25) return 4
  if (em < 93.75) return 5
  return 6
}

/**
 * Окно уже 81.25em (1300px): левое меню само сворачивается, если пользователь не выбрал сам. Место отдаётся сначала
 * за счёт меню; плавать ли правой панели, фрейм решает уже по оставшемуся месту (DesktopFrame).
 */
export function isMenuAutoCollapsed(): boolean {
  return screenScale() < 5
}
