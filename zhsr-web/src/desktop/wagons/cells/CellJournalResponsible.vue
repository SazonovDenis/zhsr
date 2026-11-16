<template>
  <span v-if="!row.responsibleChanged" class="zs-sub">—</span>
  <span v-else>
    <template v-if="row.prevResponsibleUsr !== null">
      <span class="zs-sub">{{ userName(row.prevResponsibleUsr) }}</span>
      <span class="zs-sub"> → </span>
    </template>
    <span class="cell-journal__to">{{ row.responsibleUsr ? userName(row.responsibleUsr) : t('не назначен') }}</span>
  </span>
</template>

<script setup lang="ts">
import { t } from '@/boot/i18n'
import type { CellProps } from '@/shared/components/table/types'
import { userName } from '@/shared/user'
import type { JournalRow } from '../journalColumns'

/**
 * Назначение ответственного: было → стало; прежде ответственного не было — только стало; событие ответственного не
 * назначало — прочерк.
 */
defineProps<CellProps<JournalRow>>()
</script>

<style scoped lang="scss">
.cell-journal__to {
  font-weight: 500;
}
</style>
