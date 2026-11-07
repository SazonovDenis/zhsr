<template>{{ row.wagon.number }}</template>

<script setup lang="ts">
import type { RepairRequest } from '@/api/requests'
import type { CellProps } from '@/shared/components/table/types'

/** Номер вагона заявки. */
defineProps<CellProps<RepairRequest>>()
</script>
