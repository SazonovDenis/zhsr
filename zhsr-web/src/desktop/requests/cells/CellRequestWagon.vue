<template>
  <div class="zs-mono">{{ row.wagon.number }}</div>
  <div class="zs-sub">{{ dictManager.getValue('WagonType', row.wagon.wagonType) }}</div>
</template>

<script setup lang="ts">
import type { RepairRequest } from '@/api/requests'
import type { CellProps } from '@/shared/components/table/types'
import { dictManager } from '@/shared/dict/DictManager'

/** Вагон заявки: номер и под ним тип вагона. */
defineProps<CellProps<RepairRequest>>()
</script>
