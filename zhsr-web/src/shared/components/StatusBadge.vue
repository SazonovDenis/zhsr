<template>
  <span class="badge" :class="`badge--${tone}`" :style="bg || fg ? { background: bg, color: fg } : undefined">
    <slot />
  </span>
</template>

<script setup lang="ts">
/**
 * Плашка — как статус заявки или состояние вагона: текст (слот) на цветном фоне. tone — цвет по смыслу:
 * neutral — серая, ok — зелёная (в порядке), bad — красная (нарушение, отказ), wait — жёлтая (ожидает отправки),
 * selected — цвет выбранного (свой вагон). bg и fg — свои цвета (из записи справочника), они важнее tone.
 *
 *   <StatusBadge tone="ok">В порядке</StatusBadge>
 */
withDefaults(
  defineProps<{
    tone?: 'neutral' | 'ok' | 'bad' | 'wait' | 'selected'
    bg?: string | undefined
    fg?: string | undefined
  }>(),
  { tone: 'neutral', bg: undefined, fg: undefined },
)
</script>

<style scoped lang="scss">
.badge {
  display: inline-block;
  padding: 0.1rem 0.6rem;
  // скруглённые бока: радиус не меньше половины высоты плашки
  border-radius: 1rem;
  font-size: 0.8rem;
  font-weight: 600;
  white-space: nowrap;

  &--neutral {
    background: $zs-bg-button-soft;
    color: $zs-text-muted;
  }

  &--ok {
    background: $zs-ok-bg;
    color: $zs-ok-fg;
  }

  &--bad {
    background: $zs-error-bg;
    color: $zs-error-fg;
  }

  &--wait {
    background: #fff1cc;
    color: #6b4700;
  }

  &--selected {
    background: $zs-selected-bg;
    color: $zs-selected-fg;
  }
}
</style>
