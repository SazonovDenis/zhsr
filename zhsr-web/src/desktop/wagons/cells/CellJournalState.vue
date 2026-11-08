<template>
  <span v-if="!row.statusChanged" class="zs-sub">—</span>
  <span v-else>
    <template v-if="row.prevWagonStatusType !== null">
      <span class="zs-sub">{{ dictManager.getValue('WagonState', row.prevWagonStatusType) }}</span>
      <span class="zs-sub"> → </span>
    </template>
    <span class="cell-journal__to">{{ dictManager.getValue('WagonState', row.wagonStatusType) }}</span>
  </span>
</template>

<script setup lang="ts">
import type { CellProps } from '@/shared/components/table/types'
import { dictManager } from '@/shared/dict/DictManager'
import type { JournalRow } from '../journalColumns'

/** Смена состояния: было → стало; первая запись — только стало; событие состояние не меняло — прочерк. */
defineProps<CellProps<JournalRow>>()
</script>

<style scoped lang="scss">
.cell-journal__to {
  font-weight: 500;
}
</style>
