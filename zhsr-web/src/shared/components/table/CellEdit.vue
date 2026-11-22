<template>
  <!-- нажатие уходит в строку: правку открывает клик по строке -->
  <button type="button" class="cell-edit q-pa-none" :title="t('Изменить')" :aria-label="t('Изменить')">
    <q-icon name="edit" size="1.2rem" />
  </button>
</template>

<script setup lang="ts" generic="Row">
import { t } from '@/boot/i18n'
import type { CellProps } from './types'

/**
 * Ячейка-карандаш в конце строки DataTable: размером — как три точки меню строки, цветом темнее (ярче под курсором
 * строки), с отступом справа. Своего действия нет: нажатие доходит до строки, правку открывает row-click таблицы.
 * Колонка — узкая, без заголовка: { field: 'id', title: '', width: '2.8rem', dense: true, cell: CellEdit }.
 */
defineProps<CellProps<Row>>()
</script>

<style scoped lang="scss">
.cell-edit {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 1.6rem;
  height: 1.6rem;
  margin-right: 0.5rem;
  border: 0;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.7);
  color: $zs-text;
  opacity: 0.6;
  cursor: pointer;

  :global(.data-table__row:hover) & {
    opacity: 0.9;
  }

  &:hover,
  &:focus-visible {
    background: #fff;
    opacity: 1;
    outline: none;
  }
}
</style>
