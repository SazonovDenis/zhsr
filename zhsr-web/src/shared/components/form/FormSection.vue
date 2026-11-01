<template>
  <section class="form-section" :class="{ 'form-section--divided': divided }">
    <div v-if="title" class="form-section__title">{{ t(title) }}</div>
    <div class="form-section__body" :style="{ gridTemplateColumns: columns }">
      <slot />
    </div>
  </section>
</template>

<script setup lang="ts">
import { t } from '@/boot/i18n'

/**
 * Раздел формы: заголовок прописными буквами и поля сеткой. columns — колонки сетки CSS grid, по умолчанию одна.
 * divided — линия над разделом, отделяет его от предыдущего.
 */
withDefaults(defineProps<{ title?: string; columns?: string; divided?: boolean }>(), {
  title: '',
  columns: 'minmax(0, 1fr)',
  divided: false,
})
</script>

<style scoped lang="scss">
.form-section {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;

  &--divided {
    padding-top: 1rem;
    border-top: 0.0625rem solid $zs-border-row;
  }
}

.form-section__title {
  color: $zs-text-muted;
  font-size: 0.75rem;
  font-weight: 600;
  letter-spacing: 0.06em;
  text-transform: uppercase;
}

.form-section__body {
  display: grid;
  gap: 0.875rem;
}
</style>
