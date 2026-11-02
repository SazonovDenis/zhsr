<template>
  <div class="zs-mono">{{ row.number }}</div>
  <div class="zs-sub">{{ formatDateTime(row.created) }}</div>
</template>

<script setup lang="ts">
import type { RepairRequest } from '@/api/requests'
import type { CellProps } from '@/shared/components/table/types'
import { formatDateTime } from '@/shared/format'

/** Номер заявки и время подачи. */
defineProps<CellProps<RepairRequest>>()
</script>
