import './mapMarks.scss'

import type { CircleMarker, Map as LeafletMap } from 'leaflet'
import { circleMarker } from 'leaflet'

import type { MapPoint } from '@/api/stages'

/**
 * Точки с подписями на карте Leaflet: мелкие кружки, подписи видны всегда; набор прорежен под масштаб
 * (thinnedMarks). Классы —
 * mapMarks.scss.
 */

/** Точка с подписью. */
export interface PointMark {
  id: number
  point: MapPoint
  text: string
}

/** Показанная точка: label — с какой стороны от кружка подпись; null — подписи нет. */
export interface ShownMark {
  mark: PointMark
  label: 'left' | 'right' | null
}

export type MarkKind = 'normal' | 'strong' | 'selected'

/** Вся республика: место и масштаб карты, пока показывать нечего. */
export const DEFAULT_CENTER: [number, number] = [48.0, 67.0]
export const DEFAULT_ZOOM = 5
/** Крупнее этого карта сама не показывает. */
export const POINT_ZOOM = 11

// окрестность точки: сколько ближайших
const NEAR_COUNT = 20
// сколько видно после приближения к точке, км
const FOCUS_KM = 200

// радиус кружка, пикселей: Leaflet берёт его только числом
const RADIUS: Record<MarkKind, number> = { normal: 4, strong: 10, selected: 10 }

// размеры подписи для прореживания, rem: кегль, высота строки, от середины кружка до текста, промежуток между точками
const LABEL_SIZE = 0.8
const LABEL_HEIGHT = 1.2
const LABEL_OFFSET = 0.8
const LABEL_GAP = 0.4

/** Место кружка с подписью на карте, пикселей. */
interface Box {
  left: number
  right: number
  top: number
  bottom: number
}

const canvas = document.createElement('canvas').getContext('2d')

/** Подпись элементом, а не строкой: строку Leaflet разобрал бы как HTML. */
export function markLabel(text: string): HTMLElement {
  const el = document.createElement('span')
  el.textContent = text
  return el
}

/** Приблизить карту к точке: видно не больше FOCUS_KM по большей стороне; уже не больше — карта не меняется. */
export function focusMap(m: LeafletMap, p: MapPoint): void {
  const size = m.getSize()
  if (size.x === 0 || size.y === 0) return
  // метров в пикселе у широты точки: расстояние между соседними точками экрана
  const at = m.latLngToContainerPoint([p.y, p.x])
  const perPixel = m.distance([p.y, p.x], m.containerPointToLatLng([at.x + 1, at.y]))
  const km = (Math.max(size.x, size.y) * perPixel) / 1000
  if (km <= FOCUS_KM) return
  m.setView([p.y, p.x], m.getZoom() + Math.ceil(Math.log2(km / FOCUS_KM)), { animate: true })
}

/** Ближайшие к точке, по расстоянию; долгота сжата по широте, чтобы расстояние было похоже на настоящее. */
export function nearestMarks(marks: PointMark[], p: MapPoint): PointMark[] {
  const k = Math.cos((p.y * Math.PI) / 180)
  const far = (m: PointMark) => ((m.point.x - p.x) * k) ** 2 + (m.point.y - p.y) ** 2
  return [...marks].sort((a, b) => far(a) - far(b))
}

/** Окрестность: NEAR_COUNT ближайших к каждой из points — не по километрам: где станции редки, она шире. */
export function nearMarks(marks: PointMark[], points: MapPoint[]): Set<number> {
  return new Set(points.flatMap((p) => nearestMarks(marks, p).slice(0, NEAR_COUNT)).map((mark) => mark.id))
}

/**
 * Видимые точки, прореженные: точка показывается, если её кружок с подписью не задевает показанные раньше. Порядок
 * marks — кто важнее; от места карты он зависеть не должен — тогда при сдвиге показанные точки почти не меняются.
 *
 * @param must id точек, которые прореживание не убирает: подписи тесно справа — она слева
 * @param keep id точек, у которых прореживается только подпись: тесно — кружок без подписи
 */
export function thinnedMarks(
  m: LeafletMap,
  marks: PointMark[],
  must: ReadonlySet<number> = new Set(),
  keep: ReadonlySet<number> = new Set(),
): ShownMark[] {
  const rem = parseFloat(getComputedStyle(document.documentElement).fontSize)
  if (canvas) canvas.font = `600 ${LABEL_SIZE * rem}px ${getComputedStyle(m.getContainer()).fontFamily}`
  const box = (point: MapPoint, text: string, left = false): Box => {
    const at = m.latLngToContainerPoint([point.y, point.x])
    const half = ((LABEL_HEIGHT + LABEL_GAP) * rem) / 2
    const length = LABEL_OFFSET * rem + (canvas?.measureText(text).width ?? 0) + LABEL_GAP * rem
    return {
      left: at.x - (left ? length : half),
      right: at.x + (left ? half : length),
      top: at.y - half,
      bottom: at.y + half,
    }
  }
  const taken: Box[] = []
  const view = m.getBounds()
  const res: ShownMark[] = []
  const busy = (b: Box) =>
    taken.some((o) => b.left < o.right && o.left < b.right && b.top < o.bottom && o.top < b.bottom)
  for (const mark of marks) {
    if (!view.contains([mark.point.y, mark.point.x])) continue
    const right = box(mark.point, mark.text)
    const tight = busy(right)
    if (tight && !must.has(mark.id)) {
      if (keep.has(mark.id)) res.push({ mark, label: null })
      continue
    }
    taken.push(tight ? box(mark.point, mark.text, true) : right)
    res.push({ mark, label: tight ? 'left' : 'right' })
  }
  return res
}

/**
 * Кружок точки с подписью.
 *
 * @param kind strong — выделенная: крупнее, подпись полужирная; selected — выбранная: выглядит как выделенная
 * @param onClick щелчок по кружку или подписи; null — точка не нажимается
 */
export function markCircle(
  { mark, label }: ShownMark,
  kind: MarkKind = 'normal',
  onClick: (() => void) | null = null,
): CircleMarker {
  const strong = kind !== 'normal'
  const circle = circleMarker([mark.point.y, mark.point.x], {
    className: strong ? 'map-point' : 'map-mark',
    radius: RADIUS[kind],
    interactive: !!onClick,
    bubblingMouseEvents: false,
  })
  circle.bindTooltip(markLabel(mark.text), {
    // без подписи — название при наведении (если точка нажимается)
    permanent: label !== null,
    direction: label ?? 'right',
    interactive: !!onClick,
    className: strong ? 'map-label map-label--point' : 'map-label',
  })
  if (onClick) circle.on('click', onClick)
  return circle
}
