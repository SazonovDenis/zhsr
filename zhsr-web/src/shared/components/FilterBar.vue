<template>
  <div class="filter-bar q-mb-md">
    <slot />
    <span v-if="$slots.right" class="filter-bar__right q-ml-auto"><slot name="right" /></span>
  </div>
</template>

<script setup lang="ts">
/**
 * Панель фильтров над таблицей: фильтры в ряд с переносом; слот right — справа, например «12 вагонов».
 * Поля фильтров ниже, чем в формах: панель задаёт им свою высоту (--zs-control-height).
 */
</script>

<style scoped lang="scss">
.filter-bar {
  --zs-control-height: 2.1rem;

  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.8rem;
}

.filter-bar__right {
  color: $zs-text-muted;
  font-size: 0.8rem;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}
</style>
