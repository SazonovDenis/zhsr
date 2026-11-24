import { compareText } from '@/shared/format'

/** Пункт шаблона в дереве: id, раздел-родитель, порядок показа. */
export interface TreeItem {
  id: number
  parentItem: number | null
  sortOrder: number
  textRu: string
}

/** Пункт в порядке показа дерева и его глубина: 0 — верхний уровень. */
export interface TreeRow<T> {
  item: T
  depth: number
}

/**
 * Пункты шаблона в порядке показа дерева: раздел, за ним его пункты (по sortOrder, затем по названию), вглубь. Пункт, раздел
 * которого не пришёл, показывается на верхнем уровне.
 */
export function treeRows<T extends TreeItem>(items: T[]): TreeRow<T>[] {
  const ids = new Set(items.map((i) => i.id))
  const children = new Map<number | null, T[]>()
  for (const item of items) {
    const parent = item.parentItem !== null && ids.has(item.parentItem) ? item.parentItem : null
    const list = children.get(parent) ?? []
    list.push(item)
    children.set(parent, list)
  }
  for (const list of children.values())
    list.sort((a, b) => a.sortOrder - b.sortOrder || compareText(a.textRu, b.textRu))
  const res: TreeRow<T>[] = []
  const walk = (parent: number | null, depth: number) => {
    for (const item of children.get(parent) ?? []) {
      res.push({ item, depth })
      walk(item.id, depth + 1)
    }
  }
  walk(null, 0)
  return res
}

/** Карточка пунктов на телефоне: раздел верхнего уровня (head) и его пункты вглубь. */
export interface TreeCard<T> {
  key: string
  /** раздел верхнего уровня; null — карточка пунктов верхнего уровня, идущих подряд */
  head: T | null
  /**
   * строки карточки двумя частями: все, кроме последней, и последняя. Заголовок раздела липнет к верху в пределах
   * первой части — уходит вверх, как только последняя строка доходит до заголовка. У карточки без заголовка — одна часть.
   */
  parts: TreeRow<T>[][]
}

/**
 * Строки дерева карточками по разделам верхнего уровня; пункты верхнего уровня подряд — карточка без заголовка.
 *
 * @param isSection строка — раздел, а не пункт
 */
export function treeCards<T extends TreeItem>(rows: TreeRow<T>[], isSection: (item: T) => boolean): TreeCard<T>[] {
  const cards: { key: string; head: T | null; rows: TreeRow<T>[] }[] = []
  for (const row of rows) {
    if (row.depth === 0 && isSection(row.item)) {
      cards.push({ key: `s${row.item.id}`, head: row.item, rows: [] })
      continue
    }
    let last = cards[cards.length - 1]
    if (row.depth === 0 && (!last || last.head)) {
      last = { key: `i${row.item.id}`, head: null, rows: [] }
      cards.push(last)
    }
    last!.rows.push(row)
  }
  return cards.map((c) => ({
    key: c.key,
    head: c.head,
    parts: c.head && c.rows.length > 0 ? [c.rows.slice(0, -1), c.rows.slice(-1)] : [c.rows],
  }))
}

/** Отступ строки в карточке: уровень раздела карточки — без отступа, глубже — по 0.5rem. */
export function cardIndent(depth: number): string {
  return `${1 + Math.max(depth - 1, 0) * 0.5}rem`
}

/**
 * Строки дерева, где остаются пункты match и разделы, в которых вглубь есть такие пункты; порядок и глубина прежние.
 */
export function treeRowsWhere<T>(rows: TreeRow<T>[], match: (item: T) => boolean): TreeRow<T>[] {
  return rows.filter((row, i) => {
    if (match(row.item)) return true
    // потомки раздела — строки за ним глубже него, до первой строки той же глубины или выше
    for (let j = i + 1; j < rows.length && rows[j]!.depth > row.depth; j++) {
      if (match(rows[j]!.item)) return true
    }
    return false
  })
}
