/**
 * Иконки приложения: имя файла без .svg — имя иконки.
 *
 * Чтобы добавить иконку, положите svg в эту папку и допишите имя в IconName. У svg задаётся только viewBox (без width
 * и height), а цвет линий — currentColor: размер и цвет задаёт SvgIcon.
 */
export type IconName =
  | 'logo'
  | 'wagons'
  | 'trips'
  | 'requests'
  | 'acts'
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
