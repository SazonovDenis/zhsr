<template>
  <!-- в строку: станция — точка и название, под ним стоянка и расстояние; между станциями — стрелка, над ней время
       в пути по перегону -->
  <div
    v-if="layout === 'horizontal'"
    class="route-stops route-stops--horizontal"
    :class="{ 'route-stops--toggle': hasPassing }"
    :title="hasPassing ? t('Показать или скрыть станции следования') : undefined"
    @click="hasPassing && (full = !full)"
  >
    <template v-for="(item, index) in items" :key="index">
      <!-- стрелка перед станцией; после свёрнутых станций следования стрелка у самого свёрнутого блока -->
      <div v-if="index > 0 && items[index - 1]?.kind !== 'gap'" class="route-stops__link">
        <span class="route-stops__run">{{ formatDuration(item.runDuration) }}</span>
        <span class="route-stops__arrow">
          <SvgIcon name="arrow-route" size="0.8rem" />
          <template v-if="item.kind === 'gap'">
            <span class="route-stops__gap-count">{{ item.count }}</span>
            <SvgIcon name="arrow-route" size="0.8rem" />
          </template>
        </span>
      </div>

      <div v-if="item.kind !== 'gap'" class="route-stops__station" :class="`route-stops__station--${item.kind}`">
        <span class="route-stops__head">
          <span class="route-stops__dot" />
          <span class="route-stops__name">{{ stationName(item.point) }}</span>
        </span>
        <span v-if="item.point.stopDuration > 0" class="route-stops__sub">
          {{ t('стоянка {n} мин', { n: item.point.stopDuration }) }}
        </span>
        <span class="route-stops__sub route-stops__km">{{ kmText(item.point, index) }}</span>
      </div>
    </template>
  </div>

  <!-- столбиком: линия пути с точками, название (под ним расстояние), время от отправления и стоянка справа -->
  <div
    v-else
    class="route-stops route-stops--vertical"
    :class="{ 'route-stops--toggle': hasPassing }"
    :title="hasPassing ? t('Показать или скрыть станции следования') : undefined"
    @click="hasPassing && (full = !full)"
  >
    <!-- заголовки колонок справа -->
    <div v-if="items.length > 0" class="route-stops__columns">
      <span class="route-stops__columns-space" />
      <span class="route-stops__time">{{ t('в пути, мин') }}</span>
      <span class="route-stops__stop">{{ t('стоянка') }}</span>
    </div>

    <div v-for="(item, index) in items" :key="index" class="route-stops__row" :class="`route-stops__row--${item.kind}`">
      <span class="route-stops__rail">
        <span class="route-stops__line" :class="{ 'route-stops__line--none': index === 0 }" />
        <span v-if="item.kind !== 'gap'" class="route-stops__dot" />
        <span class="route-stops__line" :class="{ 'route-stops__line--none': index === items.length - 1 }" />
      </span>

      <template v-if="item.kind === 'gap'">
        <span class="route-stops__gap-text">{{ t('{n} станций следования', item.count) }}</span>
        <!-- время свёрнутых перегонов складывается -->
        <span class="route-stops__time">{{ formatDuration(item.passDuration) }}</span>
        <span class="route-stops__stop" />
      </template>
      <template v-else>
        <span class="route-stops__title">
          <span class="route-stops__name">{{ stationName(item.point) }}</span>
          <span class="route-stops__sub route-stops__km">{{ kmText(item.point, index) }}</span>
        </span>
        <span class="route-stops__time">{{ formatDuration(item.runDuration) }}</span>
        <span class="route-stops__stop">
          {{ item.point.stopDuration > 0 ? t('{m} мин', { m: item.point.stopDuration }) : '' }}
        </span>
      </template>
    </div>
  </div>

  <div v-if="points.length === 0" class="route-stops__empty">{{ t('Остановок нет') }}</div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'

import type { RoutePoint } from '@/api/routes'
import { t } from '@/boot/i18n'
import { dictManager } from '@/shared/dict/DictManager'
import SvgIcon from '@/shared/icons/SvgIcon.vue'
import { formatHm } from './duration'

/**
 * Отображение станций маршрута по макету: станции по порядку с расстоянием от начала маршрута, временем в пути и
 * стоянками.
 *
 * - layout = 'horizontal' — в строку (шапка редактора маршрута): точка и название станции, под названием стоянка и
 *   расстояние («отправление» у начальной); над стрелкой между станциями — время в пути по перегону.
 * - layout = 'vertical' — столбиком (правая панель списка маршрутов): линия пути с точками, название, под ним
 *   расстояние; справа, под заголовками «в пути, мин» и «стоянка», — время в пути от предыдущей станции
 *   («2:32»), как над стрелками в строку, и стоянка («20 мин»).
 * - Остановки — заполненная точка и название жирным, станции следования (поезд проходит их без остановки) — пустое
 *   кольцо и серое название. Клик по компоненту сворачивает станции следования в их число между остановками и
 *   разворачивает обратно.
 */
const props = withDefaults(defineProps<{ points: RoutePoint[]; layout?: 'vertical' | 'horizontal' }>(), {
  layout: 'vertical',
})

/**
 * Элемент показа. runDuration — время в пути до элемента от предыдущего показанного, мин.
 */
type Item =
  | { kind: 'stop' | 'pass'; point: RoutePoint; runDuration: number }
  /**
   * свёрнутые станции следования. runDuration — время над свёрнутым блоком в строку: от предыдущей остановки до
   * следующей; passDuration — время столбиком: сумма свёрнутых перегонов, без перегона до следующей остановки
   */
  | { kind: 'gap'; count: number; runDuration: number; passDuration: number }

const full = ref(true)

const hasPassing = computed(() => props.points.some((p) => !p.stop))

const items = computed<Item[]>(() => {
  const result: Item[] = []
  let gap: { kind: 'gap'; count: number; runDuration: number; passDuration: number } | null = null

  for (const point of props.points) {
    if (!point.stop && !full.value) {
      if (!gap) {
        gap = { kind: 'gap', count: 0, runDuration: 0, passDuration: 0 }
        result.push(gap)
      }
      gap.count++
      gap.runDuration += point.runDuration
      gap.passDuration += point.runDuration
      continue
    }
    if (gap) {
      // путь от последней свёрнутой станции до этой остановки — тоже часть времени над свёрнутым блоком
      gap.runDuration += point.runDuration
      gap = null
    }
    result.push({ kind: point.stop ? 'stop' : 'pass', point, runDuration: point.runDuration })
  }
  return result
})

function stationName(point: RoutePoint): string {
  return dictManager.getValue('Station', point.station)
}

function kmText(point: RoutePoint, index: number): string {
  return index === 0 ? t('отправление') : t('{km} км', { km: point.km })
}

/** Время в пути — часы и минуты: «0:34», «2:32», «19:05»; ноль — пусто (у начальной станции). */
function formatDuration(min: number): string {
  return min ? formatHm(min) : ''
}

onMounted(() => dictManager.prepareDict('Station'))
</script>

<style scoped lang="scss">
.route-stops {
  font-size: 0.875rem;

  &--toggle {
    cursor: pointer;
  }
}

.route-stops__dot {
  flex: none;
  width: 0.8rem;
  height: 0.8rem;
  border-radius: 50%;
  background: $zs-route-dot;
}

// станция следования — пустое кольцо
.route-stops__station--pass .route-stops__dot,
.route-stops__row--pass .route-stops__dot {
  width: 0.5rem;
  height: 0.5rem;
  border: 0.1rem solid $zs-border-input;
  background: #fff;
}

.route-stops__name {
  color: $zs-text;
  white-space: nowrap;
}

.route-stops__station--stop .route-stops__name,
.route-stops__row--stop .route-stops__name {
  font-weight: 700;
}

.route-stops__station--pass .route-stops__name,
.route-stops__row--pass .route-stops__name {
  color: $zs-text-faint;
}

.route-stops__sub {
  color: $zs-text-muted;
  font-size: 0.8125rem;
  white-space: nowrap;
}

// --- в строку ---

.route-stops--horizontal {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  row-gap: 1.1rem;
  // место над первой строкой под время в пути над стрелками
  padding-top: 1.1rem;
}

.route-stops__station {
  display: flex;
  flex-direction: column;
}

.route-stops__head {
  display: flex;
  align-items: center;
  gap: 0.375rem;
  height: 1.3rem;
}

// подписи под названием начинаются от названия, а не от точки
.route-stops__station--stop .route-stops__sub {
  padding-left: 1.175rem;
}

.route-stops__station--pass .route-stops__sub {
  padding-left: 0.875rem;
}

.route-stops__link {
  position: relative;
  display: flex;
  align-items: center;
  height: 1.3rem;
  margin: 0 0.5rem;
  color: $zs-text-faint;
}

.route-stops__arrow {
  display: flex;
  align-items: center;
  gap: 0.1875rem;
}

.route-stops__gap-count {
  font-size: 0.75rem;
  font-weight: 600;
}

// время в пути по перегону — над стрелкой
.route-stops__run {
  position: absolute;
  bottom: 100%;
  left: 50%;
  color: $zs-text-muted;
  font-size: 0.6875rem;
  white-space: nowrap;
  transform: translateX(-50%);
}

// --- столбиком ---

.route-stops--vertical {
  display: flex;
  flex-direction: column;
}

.route-stops__row {
  display: flex;
  align-items: stretch;
  gap: 0.75rem;
  min-height: 2rem;
}

.route-stops__rail {
  display: flex;
  flex: none;
  flex-direction: column;
  align-items: center;
  width: 0.8rem;
}

.route-stops__line {
  flex: 1;
  width: 0.1rem;
  background: $zs-border-input;

  // верхний отрезок постоянной высоты: точка стоит на строке названия, а не посередине строки станции
  &:first-child {
    flex: none;
    height: 0.2rem;
  }

  &--none {
    background: transparent;
  }
}

.route-stops__row .route-stops__dot {
  margin: 0.1rem 0;
}

.route-stops__title {
  display: flex;
  flex: 1;
  flex-direction: column;
  justify-content: flex-start;
  min-width: 0;
  padding: 0.125rem 0;
  // расстояние под названием — вплотную
  line-height: 1.2;
}

// время и стоянка стоят на строке названия станции, а не посередине строки с километражем: тот же верхний отступ и
// та же высота строки, что у названия (см. .route-stops__title)
.route-stops__time,
.route-stops__stop {
  display: flex;
  flex: none;
  align-items: flex-start;
  justify-content: flex-end;
  padding: 0.125rem 0;
  color: $zs-text-muted;
  font-variant-numeric: tabular-nums;
  line-height: 1.2;
  white-space: nowrap;
}

.route-stops__time {
  width: 6.5rem;
}

.route-stops__stop {
  width: 4.5rem;
}

// километраж — мельче названия и прочих подписей
.route-stops__km {
  font-size: 0.75rem;
}

.route-stops__columns {
  display: flex;
  align-items: flex-start;
  gap: 0.75rem;
  padding-bottom: 0.375rem;
  color: $zs-text-faint;
  font-size: 0.75rem;
  line-height: 1.2;

  .route-stops__time,
  .route-stops__stop {
    align-items: flex-start;
    color: inherit;
  }
}

.route-stops__columns-space {
  flex: 1;
}

.route-stops__gap-text {
  display: flex;
  flex: 1;
  align-items: center;
  color: $zs-text-muted;
  font-size: 0.8125rem;
}

.route-stops__empty {
  color: $zs-text-muted;
}
</style>
