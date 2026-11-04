import { reactive } from 'vue'

/** Ширина левого меню: развёрнутого и свёрнутого. */
export const MENU_WIDTH = '14.5rem'
export const MENU_COLLAPSED_WIDTH = '3.5rem'

/** Группа подменю пользователя внизу меню; группы пунктов — по id в menu.json. */
export const USER_MENU_GROUP = 'user'

/**
 * Общее состояние левого меню.
 *
 * - userCollapsed — выбор пользователя: свернул (true) или развернул (false) сам; null — не выбирал, тогда меню
 *   сворачивается само.
 * - panelNeedsSpace — фрейму с правой панелью не хватает места при развёрнутом меню (решает DesktopFrame): меню
 *   сворачивается само, чтобы панель встала рядом со списком, а не плавала поверх него.
 * - openGroup — раскрытая группа с дочерними пунктами: группа пунктов или подменю пользователя; null — все свёрнуты.
 *   Раскрыта всегда только одна: раскрытие другой сворачивает прежнюю. Сама по себе, от потери фокуса, группа не
 *   сворачивается.
 */
export const menuState = reactive({
  userCollapsed: null as boolean | null,
  panelNeedsSpace: false,
  openGroup: null as string | null,
})
