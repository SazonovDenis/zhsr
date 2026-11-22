<template>
  <DictBadge v-if="row.wagonStatusTypeAt" :id="row.wagonStatusTypeAt" dict="WagonState" class="cell-attach-state-at" />
  <div v-else class="zs-sub">—</div>
  <div v-if="busy" class="cell-attach-state-at__busy">{{ busy }}</div>
</template>

<script setup lang="ts">
import { computed, inject } from 'vue'

import type { WagonRow } from '@/api/wagons'
import DictBadge from '@/shared/components/DictBadge.vue'
import type { CellProps } from '@/shared/components/table/types'
import { ATTACH_PICK } from '../attachPick'

/**
 * Состояние вагона на момент прицепки плашкой (как в списке вагонов); под ним — занятость в поезде на этот момент:
 * «Занят: поезд Тальго-659, с 21.11 20:19 по 26.11 23:49».
 */
const props = defineProps<CellProps<WagonRow>>()

const pick = inject(ATTACH_PICK)!
const busy = computed(() => pick.fit(props.row).busy)
</script>

<style scoped lang="scss">
// как в колонке текущего состояния: плашка выступает влево на свой отступ
.cell-attach-state-at {
  margin-left: -0.5rem;
}

.cell-attach-state-at__busy {
  color: $zs-error-fg;
  font-size: 0.7rem;
  font-weight: 600;
}
</style>
