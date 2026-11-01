import { reactive } from 'vue'

/** Ширина левого меню: развёрнутого и свёрнутого. */
export const MENU_WIDTH = '14.5rem'
export const MENU_COLLAPSED_WIDTH = '3.5rem'

/**
 * Общее состояние левого меню.
 *
 * - userCollapsed — выбор пользователя: свернул (true) или развернул (false) сам; null — не выбирал, тогда меню
 *   сворачивается само.
 * - panelNeedsSpace — фрейму с правой панелью не хватает места при развёрнутом меню (решает DesktopFrame): меню
 *   сворачивается само, чтобы панель встала рядом со списком, а не плавала поверх него.
 */
export const menuState = reactive({
  userCollapsed: null as boolean | null,
  panelNeedsSpace: false,
})
