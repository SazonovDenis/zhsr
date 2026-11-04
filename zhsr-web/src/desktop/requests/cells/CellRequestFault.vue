<template>
  <div class="cell-request-fault">
    <span class="cell-request-fault__name">{{ faultName(row.faultType) }}</span>
    <span v-if="row.tor" class="cell-request-fault__tor">{{ t('ТОР') }}</span>
  </div>
  <div v-if="row.description" class="zs-sub cell-request-fault__desc">{{ row.description }}</div>
</template>

<script setup lang="ts">
import type { RepairRequest } from '@/api/requests'
import { t } from '@/boot/i18n'
import type { CellProps } from '@/shared/components/table/types'
import { faultName } from '@/shared/requests/requestView'

/** Неисправность и начало описания одной строкой; отметка ТОР — вагон отцеплен в ремонт. */
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

.cell-request-fault__tor {
  padding: 0 0.375rem;
  border-radius: 0.25rem;
  background: #fbe1de;
  color: #a3261b;
  font-size: 0.6875rem;
  font-weight: 700;
}

.cell-request-fault__desc {
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
</style>
