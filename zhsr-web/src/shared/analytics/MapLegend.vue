<template>
  <div ref="root" class="map-legend" :class="manual ? '' : 'map-legend--' + corner" :style="manualStyle">
    <div
      class="map-legend__head q-px-sm"
      :class="{ 'map-legend__head--dragging': dragging }"
      @pointerdown="startDrag"
      @pointermove="drag"
      @pointerup="stopDrag"
      @pointercancel="stopDrag"
    >
      {{ title }}
    </div>
    <div class="map-legend__body q-py-xs q-px-sm"><slot /></div>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, shallowRef } from 'vue'

/**
 * Плашка поверх карты: узкий заголовок, за который её можно таскать, и содержимое. Место — от ближайшего
 * позиционированного предка.
 *
 * Сама стоит в одном из углов, сначала в левом нижнем. Закрывает рисунок карты (covered), а в другом углу закроет заметно
 * меньше (JUMP_REM и JUMP_SHARE) — перескакивает туда; проверку запускает владелец (check), когда рисунок сдвинулся.
 * Перетащили — стоит там, куда поставили, пока рисунок не сдвинется и не окажется под ней.
 */
const props = defineProps<{
  /** заголовок, переведённый */
  title: string
  /** сколько рисунка закрыто прямоугольником, пикселей длины линий; координаты — от карты */
  covered: (rect: LegendRect) => number
}>()

export interface LegendRect {
  left: number
  top: number
  width: number
  height: number
}

type Corner = 'bl' | 'br' | 'tl' | 'tr'

/** Отступы углов в rem, те же — в стилях: слева вверху — кнопки масштаба, справа внизу — подпись OSM. */
const CORNERS: Record<Corner, { x: number; y: number }> = {
  bl: { x: 0.5, y: 0.5 },
  tr: { x: 0.5, y: 2.3 },
  tl: { x: 3.5, y: 0.5 },
  br: { x: 0.5, y: 1.8 },
}
/** Перескок — если закрыто станет меньше хотя бы на столько rem и во столько раз. */
const JUMP_REM = 4
const JUMP_SHARE = 0.5

const root = shallowRef<HTMLElement | null>(null)
const corner = ref<Corner>('bl')
/** Место, куда перетащили, rem от левого верхнего угла карты; null — не перетаскивали. */
const manual = ref<{ left: number; top: number } | null>(null)
const dragging = ref(false)
let grab = { x: 0, y: 0 }
let observer: ResizeObserver | null = null

const manualStyle = computed(() =>
  manual.value ? { left: manual.value.left + 'rem', top: manual.value.top + 'rem' } : {},
)

function remPx(): number {
  return parseFloat(getComputedStyle(document.documentElement).fontSize)
}

function cornerRect(c: Corner, box: HTMLElement, el: HTMLElement): LegendRect {
  const rem = remPx()
  const { x, y } = CORNERS[c]
  const { offsetWidth: width, offsetHeight: height } = el
  return {
    left: c.endsWith('l') ? x * rem : box.clientWidth - width - x * rem,
    top: c.startsWith('t') ? y * rem : box.clientHeight - height - y * rem,
    width,
    height,
  }
}

function manualRect(at: { left: number; top: number }, el: HTMLElement): LegendRect {
  const rem = remPx()
  return { left: at.left * rem, top: at.top * rem, width: el.offsetWidth, height: el.offsetHeight }
}

/** Рисунок сдвинулся или плашка сменила размер: остаться на месте или перескочить. */
function check() {
  const el = root.value
  const box = el?.offsetParent
  if (!el || !(box instanceof HTMLElement)) return
  if (dragging.value) return
  // перетащенная не должна выйти за края, когда карта или плашка сменили размер
  if (manual.value) place(manual.value.left * remPx(), manual.value.top * remPx())
  const current = props.covered(manual.value ? manualRect(manual.value, el) : cornerRect(corner.value, box, el))
  let best: Corner | null = null
  let least = current
  for (const c of Object.keys(CORNERS) as Corner[]) {
    const value = props.covered(cornerRect(c, box, el))
    if (value < least) {
      best = c
      least = value
    }
  }
  if (best && current - least >= JUMP_REM * remPx() && least <= current * JUMP_SHARE) {
    manual.value = null
    corner.value = best
  }
}

/** Поставить в место (пиксели от угла карты), не выходя за края. */
function place(left: number, top: number) {
  const el = root.value
  const box = el?.offsetParent
  if (!el || !(box instanceof HTMLElement)) return
  const rem = remPx()
  const x = Math.max(0, Math.min(left, box.clientWidth - el.offsetWidth)) / rem
  const y = Math.max(0, Math.min(top, box.clientHeight - el.offsetHeight)) / rem
  if (manual.value?.left !== x || manual.value.top !== y) manual.value = { left: x, top: y }
}

function startDrag(e: PointerEvent) {
  const el = root.value
  if (!el || e.button !== 0) return
  e.preventDefault()
  grab = { x: e.clientX - el.offsetLeft, y: e.clientY - el.offsetTop }
  dragging.value = true
  ;(e.currentTarget as HTMLElement).setPointerCapture(e.pointerId)
}

function drag(e: PointerEvent) {
  if (dragging.value) place(e.clientX - grab.x, e.clientY - grab.y)
}

function stopDrag() {
  dragging.value = false
}

onMounted(() => {
  if (!root.value) return
  observer = new ResizeObserver(check)
  observer.observe(root.value)
})
onBeforeUnmount(() => observer?.disconnect())

defineExpose({ check })
</script>

<style scoped lang="scss">
// поверх слоёв Leaflet (их z-index — до 1000)
.map-legend {
  position: absolute;
  z-index: 1000;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  max-width: calc(100% - 4rem);
  max-height: 50%;
  border: 0.1rem solid $zs-border;
  border-radius: 0.3rem;
  background: rgb(255 255 255 / 85%);
  font-size: 0.8rem;

  &--bl {
    bottom: 0.5rem;
    left: 0.5rem;
  }

  // ниже плашки «по факту / по плану» в правом верхнем углу карты
  &--tr {
    top: 2.3rem;
    right: 0.5rem;
  }

  &--tl {
    top: 0.5rem;
    left: 3.5rem;
  }

  &--br {
    right: 0.5rem;
    bottom: 1.8rem;
  }
}

.map-legend__head {
  flex: none;
  background: $zs-bg-header;
  line-height: 1.7;
  cursor: grab;
  user-select: none;
  // палец таскает плашку, а не прокручивает страницу
  touch-action: none;

  &--dragging {
    cursor: grabbing;
  }
}

.map-legend__body {
  overflow-y: auto;
  min-height: 0;
}
</style>
