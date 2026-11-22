<template>
  <template v-if="transfer.away(row)">
    <div class="cell-trip-away__warn">{{ t('не на станции отправления') }}</div>
    <button
      type="button"
      class="cell-trip-away__link q-pa-none"
      :disabled="!!transfer.reason(row)"
      :title="transfer.reason(row) || undefined"
      @click.stop="transfer.open(row)"
    >
      {{ t('Оформить передачу') }} →
    </button>
  </template>
</template>

<script setup lang="ts">
import { inject } from 'vue'

import type { TrainWagon } from '@/api/trains'
import { t } from '@/boot/i18n'
import type { CellProps } from '@/shared/components/table/types'
import { TRIP_TRANSFER } from '../tripMoment'

/**
 * Вагон состава на отправление рейса не на станции отправления: предупреждение и ссылка «Оформить передачу» — акт
 * передачи на станцию отправления (Ф-АКТ-02); нет права или вагон не в депо и не в парке отстоя — ссылка неактивна,
 * причина — в подсказке. Вагон на станции отправления — ячейка пустая.
 */
defineProps<CellProps<TrainWagon>>()

const transfer = inject(TRIP_TRANSFER)!
</script>

<style scoped lang="scss">
.cell-trip-away__warn {
  color: $zs-error-fg;
  font-weight: 600;
}

.cell-trip-away__link {
  border: 0;
  background: none;
  color: $primary;
  font: inherit;
  font-weight: 600;
  cursor: pointer;

  &:disabled {
    color: $zs-text-muted;
    cursor: default;
  }
}
</style>
