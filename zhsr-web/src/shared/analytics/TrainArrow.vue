<template>
  <svg class="train-arrow" :class="{ 'train-arrow--future': state === 'future' }" viewBox="0 0 24 24">
    <circle cx="12" cy="12" r="10.5" />
    <path v-if="state === 'active'" d="M12 4 17.5 17.5 12 14.5 6.5 17.5Z" :transform="`rotate(${bearing} 12 12)`" />
  </svg>
</template>

<script setup lang="ts">
/**
 * Значок рейса на карте и в подписях: рейс в пути (active) — стрелка по ходу в контуре кружка, законченный (done) —
 * только кружок, будущий (future) — серый кружок.
 * bearing — направление хода, градусы от севера по часовой стрелке.
 */
withDefaults(defineProps<{ bearing?: number; state?: 'active' | 'done' | 'future' }>(), { bearing: 0, state: 'active' })
</script>

<style scoped lang="scss">
.train-arrow {
  display: block;
  flex: none;
  width: 1.2rem;
  height: 1.2rem;
  overflow: visible;

  circle {
    fill: #fff;
    stroke: #22303f;
    // единицы рисунка: сторона — 24
    stroke-width: 2;
  }

  path {
    fill: #22303f;
  }

  &--future circle {
    stroke: #b4b9c1;
  }
}
</style>
