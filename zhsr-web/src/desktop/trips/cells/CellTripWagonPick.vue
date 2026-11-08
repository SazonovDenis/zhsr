<template>
  <div class="cell-wagon-pick">
    <q-checkbox
      :model-value="pick.isPicked(row)"
      :disable="!!fit.block"
      :title="fit.block || undefined"
      dense
      @click.stop
      @update:model-value="pick.toggle(row)"
    />
    <span class="zs-mono">{{ row.number }}</span>
  </div>
  <div v-if="fit.block" class="cell-wagon-pick__block">{{ fit.block }}</div>
  <div v-else-if="fit.warn" class="cell-wagon-pick__warn">{{ fit.warn }}</div>
</template>

<script setup lang="ts">
import { computed, inject } from 'vue'

import type { WagonRow } from '@/api/wagons'
import type { CellProps } from '@/shared/components/table/types'
import { WAGON_PICK } from '../wagonPick'

/**
 * Номер вагона с отметкой выбора в окне «Добавить вагоны». Вагон в другом рейсе — отметка серая, причина под номером;
 * не на станции отправления к моменту отправления — предупреждение, выбрать можно.
 */
const props = defineProps<CellProps<WagonRow>>()

const pick = inject(WAGON_PICK)!
const fit = computed(() => pick.fit(props.row))
</script>

<style scoped lang="scss">
.cell-wagon-pick {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.cell-wagon-pick__block {
  color: $zs-error-fg;
  font-size: 0.7rem;
  font-weight: 600;
}

.cell-wagon-pick__warn {
  color: $zs-text-muted;
  font-size: 0.7rem;
  font-style: italic;
}
</style>
