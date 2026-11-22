<template>
  <div>{{ row.fullName }}</div>
  <div v-if="row.login" class="zs-sub zs-mono">{{ row.login }}</div>
</template>

<script setup lang="ts">
import type { StaffRow } from '@/api/staff'
import type { CellProps } from '@/shared/components/table/types'

/** ФИО сотрудника, под ним серым — логин (виден только администратору). */
defineProps<CellProps<StaffRow>>()
</script>
