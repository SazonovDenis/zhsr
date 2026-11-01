import type { IconName } from '@/shared/icons/icons'
import menuJson from './menu.json'

/**
 * Пункт левого меню.
 *
 * Пункт при выборе отправляет в шину событие `menu-<id>`. Переход выполняет layout: если у пункта задан route, он
 * переходит на маршрут с этим именем. Пункт без route только отправляет событие.
 */
export interface MenuItem {
  id: string
  /** текст пункта на русском; переводится при показе */
  label: string
  icon?: IconName
  /** имя маршрута в src/router/routes.ts */
  route?: string
  /**
   * постоянный вложенный пункт: в развёрнутом меню — с отступом и без иконки, в свёрнутом — иконкой, если она задана,
   * иначе первыми двумя буквами названия («Ма»)
   */
  sub?: boolean
}

export interface MenuGroup extends MenuItem {
  items: MenuItem[]
}

export interface Menu {
  items: MenuItem[]
  admin: MenuGroup
}

export const menu = menuJson as Menu

/** Имя события шины для пункта меню. */
export function menuEvent(item: MenuItem): `menu-${string}` {
  return `menu-${item.id}`
}

/** Все пункты меню, включая пункты групп. */
export function allMenuItems(): MenuItem[] {
  return [...menu.items, ...menu.admin.items]
}
