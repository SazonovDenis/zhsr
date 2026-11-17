<template>
  <span class="inline-badge" :class="`inline-badge--${tone}`"><slot /></span>
</template>

<script setup lang="ts">
/**
 * Инлайн-плашка — плашка внутри строки текста: тем же кеглем и той же высотой строки, что окружающий текст, поэтому
 * стоит на его базовой линии и не раздвигает строку. Для пометок рядом со словом, например «вы» у ФИО. Отдельная
 * плашка статуса — StatusBadge. Отступ от соседнего слова — обычный пробел в разметке. tone — цвет по смыслу: neutral — серая, контуром без заливки; selected — цвет выбранного.
 *
 *   Ахметов Б. К. <InlineBadge>вы</InlineBadge>
 */
withDefaults(defineProps<{ tone?: 'neutral' | 'selected' }>(), { tone: 'neutral' })
</script>

<style scoped lang="scss">
.inline-badge {
  display: inline;
  padding: 0 0.4em 0.1em;
  border-radius: 1em;
  font-size: inherit;
  font-weight: 500;
  line-height: inherit;
  white-space: nowrap;

  // серая — без заливки, тонким светлым контуром
  &--neutral {
    border: 0.0625rem solid $zs-border;
    color: $zs-text-muted;
  }

  &--selected {
    background: $zs-selected-bg;
    color: $zs-selected-fg;
  }
}
</style>
