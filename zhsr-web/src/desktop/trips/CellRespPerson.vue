<template>
  <UserOption :name="row.name" :me="row.me" :duty="row.duty" />
</template>

<script setup lang="ts">
import UserOption from '@/shared/components/UserOption.vue'
import type { CellProps } from '@/shared/components/table/types'
import type { PersonLine } from './TripResponsiblesBoard.vue'

/**
 * Сотрудник в таблице «По бригаде» ответственных — как в списке на телефоне: ФИО (себя — с плашкой «вы»), под ним
 * должность или служба (UserOption).
 */
defineProps<CellProps<PersonLine>>()
</script>
