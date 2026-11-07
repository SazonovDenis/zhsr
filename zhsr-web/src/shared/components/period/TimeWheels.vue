<template>
  <div class="time-wheels" :class="{ 'time-wheels--disabled': disabled }">
    <template v-for="(wheel, w) in WHEELS" :key="wheel.kind">
      <span v-if="w > 0" class="time-wheels__colon">:</span>
      <div class="time-wheels__column">
        <!-- неподвижный кружок посередине: цифры прокручиваются сквозь него, выбранная — та, что в нём -->
        <span class="time-wheels__circle" />
        <div :ref="(el) => (els[w] = el as HTMLElement | null)" class="time-wheels__wheel" @scroll="onScroll(w)">
          <div class="time-wheels__pad" />
          <div
            v-for="(value, i) in wheel.items"
            :key="i"
            class="time-wheels__item"
            :class="{ 'time-wheels__item--active': i === centered[w] }"
            @click="scrollTo(w, i, true)"
          >
            <span>{{ pad2(value) }}</span>
          </div>
          <div class="time-wheels__pad" />
        </div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, watch } from 'vue'

/**
 * Время барабанами, как в игровом автомате: часы и минуты прокручиваются пальцем и встают на значение в полосе
 * посередине; нажатие на значение прокручивает к нему. Барабан бесконечный: список повторён трижды, и после остановки
 * барабан незаметно переставляется на среднюю копию. Значение — «HH:mm»; null — время не выбрано, барабаны на 00.
 * disabled — время без даты не выбирается.
 */
const props = withDefaults(defineProps<{ disabled?: boolean }>(), { disabled: false })

const model = defineModel<string | null>({ default: null })

const COPIES = 3
const range = (n: number) => Array.from({ length: n }, (_, i) => i)
const WHEELS = [
  { kind: 'hour', count: 24, items: Array.from({ length: COPIES }, () => range(24)).flat() },
  { kind: 'minute', count: 60, items: Array.from({ length: COPIES }, () => range(60)).flat() },
]

const pad2 = (n: number) => String(n).padStart(2, '0')

const els: (HTMLElement | null)[] = []

/** Часы и минуты значения. */
const current = computed(() => {
  const [h, m] = (model.value ?? '00:00').split(':').map(Number)
  return [h ?? 0, m ?? 0]
})

/** Высота одного значения барабана в пикселях — по вёрстке. */
function itemHeight(el: HTMLElement): number {
  return el.querySelector<HTMLElement>('.time-wheels__item')?.offsetHeight ?? 1
}

/** Прокрутить барабан к позиции списка; smooth — плавно (нажатие), иначе сразу. */
function scrollTo(w: number, index: number, smooth = false) {
  const el = els[w]
  if (!el) return
  el.scrollTo({ top: index * itemHeight(el), behavior: smooth ? 'smooth' : 'instant' })
}

/** Поставить барабаны на значение — на средней копии списка. */
function show() {
  WHEELS.forEach((wheel, w) => scrollTo(w, wheel.count + current.value[w]!))
}

// барабан остановился — его значение применяется, сам он переставляется на среднюю копию
const timers: (ReturnType<typeof setTimeout> | undefined)[] = []

// позиция списка, которая сейчас в кружке, — меняется прямо при прокрутке: цифра в кружке всегда белая
const centered = reactive<number[]>(WHEELS.map((wheel) => wheel.count))

function onScroll(w: number) {
  const el = els[w]
  if (el) centered[w] = Math.round(el.scrollTop / itemHeight(el))
  clearTimeout(timers[w])
  timers[w] = setTimeout(() => settle(w), 120)
}

function settle(w: number) {
  const el = els[w]
  const wheel = WHEELS[w]
  if (!el || !wheel || props.disabled) return
  const index = Math.round(el.scrollTop / itemHeight(el))
  const value = index % wheel.count
  if (index < wheel.count || index >= wheel.count * 2) scrollTo(w, wheel.count + value)
  const next = [...current.value]
  next[w] = value
  const time = `${pad2(next[0]!)}:${pad2(next[1]!)}`
  if (time !== model.value) model.value = time
}

onMounted(() => void nextTick(show))
onBeforeUnmount(() => timers.forEach((t) => clearTimeout(t)))

// значение сменилось снаружи (например, «Очистить») — барабаны встают на него
watch(model, () => {
  WHEELS.forEach((wheel, w) => {
    const el = els[w]
    if (el && Math.round(el.scrollTop / itemHeight(el)) % wheel.count !== current.value[w]) {
      scrollTo(w, wheel.count + current.value[w]!)
    }
  })
})
</script>

<style scoped lang="scss">
// пять значений в окне, выбранное — посередине
.time-wheels {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;

  &--disabled {
    opacity: 0.5;
    pointer-events: none;
  }
}

.time-wheels__column {
  position: relative;
}

// светлый кружок, как выбранный день календаря; стоит на месте, барабан прокручивается поверх
.time-wheels__circle {
  position: absolute;
  top: 50%;
  left: 50%;
  width: 2rem;
  height: 2rem;
  border-radius: 50%;
  background: $zs-selected-bg;
  box-shadow: inset 0 0 0 0.0625rem $zs-selected-border;
  transform: translate(-50%, -50%);
  pointer-events: none;
}

.time-wheels__wheel {
  position: relative;
  width: 4rem;
  height: 12.5rem;
  overflow-y: auto;
  scroll-snap-type: y mandatory;
  scrollbar-width: none;
  // края барабана уходят в тень, как у настоящего
  mask-image: linear-gradient(transparent, #000 35%, #000 65%, transparent);

  &::-webkit-scrollbar {
    display: none;
  }
}

.time-wheels__pad {
  height: 5rem;
}

.time-wheels__item {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 2.5rem;
  color: $zs-text;
  // на 10% крупнее цифр дня календаря
  font-size: calc(var(--zs-calendar-font-size) * 1.1);
  font-variant-numeric: tabular-nums;
  scroll-snap-align: center;
  cursor: pointer;

  // выбранное — в неподвижном кружке, как выбранный день календаря: белым, тем же начертанием
  &--active {
    color: $zs-selected-fg;
  }
}

.time-wheels__colon {
  position: relative;
  color: $zs-text;
  // как цифры барабанов
  font-size: calc(var(--zs-calendar-font-size) * 1.1);
}
</style>
