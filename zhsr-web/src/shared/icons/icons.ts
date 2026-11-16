/**
 * Иконки приложения: имя файла без .svg — имя иконки.
 *
 * Логотип — два эталона: logo.svg (светлая тема) и logo-dark.svg (тёмная тема), знак без фона; всё остальное с
 * логотипом — производные (public/favicon.svg, public/icons/*.png).
 *
 * Чтобы добавить иконку, положите svg в эту папку и допишите имя в IconName. У svg задаётся только viewBox (без width
 * и height), а цвет линий — currentColor: размер и цвет задаёт SvgIcon.
 */
export type IconName =
  | 'logo'
  // логотипы: цвета свои, не currentColor
  | 'logo-dark'
  | 'wagons'
  | 'trips'
  | 'requests'
  | 'acts'
  // не из макета: чек-листы и экипировка, нарисованы в том же стиле
  | 'checks'
  | 'equip'
  | 'warranty'
  | 'analytics'
  | 'settings'
  | 'chevron-down'
  | 'arrow-back'
  | 'arrow-route'
  // не из макета: в макете у пользователя иконки нет, нарисована в том же стиле
  | 'user'

const files = import.meta.glob<string>('./*.svg', { query: '?raw', import: 'default', eager: true })

export const icons = Object.fromEntries(Object.entries(files).map(([path, svg]) => [path.slice(2, -4), svg])) as Record<
  IconName,
  string
>
