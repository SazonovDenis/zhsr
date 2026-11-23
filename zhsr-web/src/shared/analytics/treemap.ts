/** Прямоугольник древовидной карты. */
export interface TreemapRect {
  x: number
  y: number
  w: number
  h: number
}

/** Наихудшее отношение сторон у ряда площадей вдоль стороны side. */
function worst(areas: number[], side: number): number {
  const sum = areas.reduce((a, v) => a + v, 0)
  return Math.max(...areas.map((v) => Math.max((side * side * v) / (sum * sum), (sum * sum) / (side * side * v))))
}

/**
 * Раскладка древовидной карты (squarified): прямоугольники с площадями пропорционально values, близкие к квадратам.
 * values — больше нуля, по убыванию; результат — в том же порядке.
 */
export function treemap(values: number[], box: TreemapRect): TreemapRect[] {
  const total = values.reduce((a, v) => a + v, 0)
  if (!total || box.w <= 0 || box.h <= 0) return values.map(() => ({ x: box.x, y: box.y, w: 0, h: 0 }))
  const areas = values.map((v) => (v / total) * box.w * box.h)
  const res: TreemapRect[] = []
  let { x, y, w, h } = box
  let i = 0
  while (i < areas.length) {
    const side = Math.min(w, h)
    let end = i + 1
    while (end < areas.length && worst(areas.slice(i, end + 1), side) <= worst(areas.slice(i, end), side)) end++
    const row = areas.slice(i, end)
    const depth = row.reduce((a, v) => a + v, 0) / side
    let offset = 0
    for (const area of row) {
      const length = area / depth
      res.push(w >= h ? { x, y: y + offset, w: depth, h: length } : { x: x + offset, y, w: length, h: depth })
      offset += length
    }
    if (w >= h) {
      x += depth
      w -= depth
    } else {
      y += depth
      h -= depth
    }
    i = end
  }
  return res
}
