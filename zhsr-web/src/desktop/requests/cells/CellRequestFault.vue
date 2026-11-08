<template>
  <div class="cell-request-fault">
    <span class="cell-request-fault__name">{{ faultName(row.faultType) }}</span>
  </div>
  <div v-if="row.description" class="zs-sub cell-request-fault__desc">{{ row.description }}</div>
</template>

<script setup lang="ts">
import type { RepairRequest } from '@/api/requests'
import type { CellProps } from '@/shared/components/table/types'
import { faultName } from '@/shared/requests/requestView'

/** Неисправность и начало описания одной строкой. */
defineProps<CellProps<RepairRequest>>()
</script>

<style scoped lang="scss">
.cell-request-fault {
  display: flex;
  align-items: baseline;
  gap: 0.5rem;
}

.cell-request-fault__name {
  font-weight: 600;
  line-height: 1.3;
}

.cell-request-fault__desc {
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
</style>
