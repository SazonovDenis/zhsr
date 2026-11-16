<template>
  <div class="cell-order">
    <template v-if="ctx">
      <button
        v-for="up in [false, true]"
        :key="String(up)"
        type="button"
        class="cell-order__btn q-pa-none"
        :disabled="running || !!ctx.reason() || !ctx.canMove(row, up)"
        :title="ctx.reason() || (up ? t('Выше') : t('Ниже'))"
        @click.stop="move(up)"
        @dblclick.stop
      >
        {{ up ? '↑' : '↓' }}
      </button>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'

import { t } from '@/boot/i18n'
import type { CellProps } from '@/shared/components/table/types'
import { notifyError } from '@/shared/errors'
import { useSortOrder } from './sortOrder'

/**
 * Ячейка «Порядок показа»: кнопки «выше» и «ниже», без числа. Кнопка недоступна, если таблица отсортирована не по
 * порядку показа (причина — во всплывающей подсказке) или строка первая (последняя) в своей группе.
 */
const props = defineProps<CellProps<unknown>>()
const ctx = useSortOrder()
const running = ref(false)

async function move(up: boolean) {
  if (!ctx) return
  running.value = true
  try {
    await ctx.move(props.row, up)
  } catch (e) {
    // ошибка действия из ячейки таблицы: своего места у неё нет — всплывашка
    notifyError(e)
  } finally {
    running.value = false
  }
}
</script>

<style scoped lang="scss">
.cell-order {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.2rem;
}

.cell-order__btn {
  width: 2.5em;
  height: 1.8em;
  border: 0.0625rem solid $zs-border-input;
  border-radius: 0.2rem;
  background: #fff;
  color: $zs-text-muted;
  font-size: 0.8rem;
  line-height: 1;
  cursor: pointer;

  &:hover:not(:disabled) {
    color: $primary;
    border-color: $primary;
  }

  &:disabled {
    opacity: 0.3;
    cursor: default;
  }
}
</style>
