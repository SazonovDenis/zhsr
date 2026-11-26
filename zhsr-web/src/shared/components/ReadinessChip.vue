<template>
  <span class="readiness-chip q-py-none q-px-sm" :class="ok ? 'readiness-chip--ok' : 'readiness-chip--bad'">
    <slot />
  </span>
</template>

<script setup lang="ts">
/**
 * Плашка пункта готовности — в списке рейсов и у вагона состава: ok — пункт выполнен (зелёная), иначе не выполнен
 * (красная).
 *
 *   <ReadinessChip ok>Проводники по норме</ReadinessChip>
 */
defineProps<{ ok: boolean }>()
</script>

<style scoped lang="scss">
.readiness-chip {
  display: inline-block;
  border-radius: 62.5rem;
  font-size: 0.8rem;
  line-height: 1.35;

  &--ok {
    background: #dff0e2;
    color: #1d5e2c;
  }

  &--bad {
    background: #f7dcd8;
    color: $zs-error-fg;
  }
}
</style>
