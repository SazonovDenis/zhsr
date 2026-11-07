<template>
  <div class="cell-trip-readiness">
    <span
      v-for="item in row.readiness"
      :key="item.text"
      class="cell-trip-readiness__chip q-py-none q-px-sm"
      :class="`cell-trip-readiness__chip--${item.level}`"
    >
      {{ t(item.text) }}
    </span>
  </div>
</template>

<script setup lang="ts">
import type { TripRow } from '@/api/trips'
import { t } from '@/boot/i18n'
import type { CellProps } from '@/shared/components/table/types'

/** Готовность рейса: пункты цветными чипами — готово, есть замечания, не готово. */
defineProps<CellProps<TripRow>>()
</script>

<style scoped lang="scss">
.cell-trip-readiness {
  display: flex;
  flex-wrap: wrap;
  gap: 0.4rem;
}

.cell-trip-readiness__chip {
  border-radius: 62.5rem;
  font-size: 0.8rem;
  font-weight: 600;
  line-height: 1.35;

  &--ok {
    background: #dff0e2;
    color: #1d5e2c;
  }

  &--warn {
    background: #fbe9cf;
    color: #8a4b05;
  }

  &--bad {
    background: #f7dcd8;
    color: $zs-error-fg;
  }
}
</style>
