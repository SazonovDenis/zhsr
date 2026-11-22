<template>
  <span v-if="!row.responsibleChanged" class="zs-sub">—</span>
  <span v-else>
    <template v-if="row.prevResponsibleUsrs.length > 0">
      <span class="zs-sub"><UserNames :users="row.prevResponsibleUsrs" /></span>
      <span class="zs-sub"> → </span>
    </template>
    <span class="cell-journal__to"><UserNames :users="row.responsibleUsrs" /></span>
  </span>
</template>

<script setup lang="ts">
import type { CellProps } from '@/shared/components/table/types'
import type { JournalRow } from '../journalColumns'

import UserNames from '@/shared/components/UserNames.vue'
/**
 * Смена ответственных: были → стали; прежде ответственных не было — только стали; событие ответственных не меняло —
 * прочерк.
 */
defineProps<CellProps<JournalRow>>()
</script>

<style scoped lang="scss">
.cell-journal__to {
  font-weight: 500;
}
</style>
