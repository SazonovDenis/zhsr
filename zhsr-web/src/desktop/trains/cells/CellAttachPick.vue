<template>
  <div class="cell-attach-pick">
    <!-- занятый в поезде — замок вместо галочки; чем занят — в колонке «Состояние на момент» -->
    <q-icon v-if="fit.busy" name="lock" size="1.2rem" class="cell-attach-pick__lock" :title="fit.busy" />
    <q-checkbox
      v-else
      :model-value="pick.isPicked(row)"
      :disable="!!fit.block"
      :title="fit.block || undefined"
      dense
      @click.stop
      @update:model-value="pick.toggle(row)"
    />
    <span class="zs-mono">{{ row.number }}</span>
  </div>
  <div v-if="!fit.busy && fit.block" class="cell-attach-pick__block">{{ fit.block }}</div>
  <div v-else-if="fit.warn" class="cell-attach-pick__warn">{{ fit.warn }}</div>
</template>

<script setup lang="ts">
import { computed, inject } from 'vue'

import type { WagonRow } from '@/api/wagons'
import type { CellProps } from '@/shared/components/table/types'
import { ATTACH_PICK } from '../attachPick'

/**
 * Номер вагона с отметкой выбора в окне «Прицепить вагоны». Вагон, занятый в поезде, — замок вместо отметки; другая
 * причина, по которой вагон не выбирается, — отметка серая, причина под номером; предупреждение — под номером, выбрать
 * можно.
 */
const props = defineProps<CellProps<WagonRow>>()

const pick = inject(ATTACH_PICK)!
const fit = computed(() => pick.fit(props.row))
</script>

<style scoped lang="scss">
.cell-attach-pick {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.cell-attach-pick__lock {
  color: $zs-text-muted;
}

.cell-attach-pick__block {
  color: $zs-error-fg;
  font-size: 0.7rem;
  font-weight: 600;
}

.cell-attach-pick__warn {
  color: $zs-text-muted;
  font-size: 0.7rem;
  font-style: italic;
}
</style>
