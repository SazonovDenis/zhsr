<template>
  <WagonOption :number="row.number" :type="row.type" />
</template>

<script setup lang="ts">
import WagonOption from '@/shared/components/WagonOption.vue'
import type { CellProps } from '@/shared/components/table/types'
import type { WagonLine } from './TripResponsiblesBoard.vue'

/** Вагон в таблице «По вагонам» ответственных — как в списке на телефоне: номер, под ним тип (WagonOption). */
defineProps<CellProps<WagonLine>>()
</script>
