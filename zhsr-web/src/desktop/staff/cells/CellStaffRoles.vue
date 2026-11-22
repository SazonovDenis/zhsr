<template>
  <div>{{ row.roles.map((id) => dictManager.getValue('Role', id)).join(', ') }}</div>
  <div v-if="row.categoryType !== null" class="zs-sub">
    {{ dictManager.getValue('ConductorCategory', row.categoryType) }}
  </div>
</template>

<script setup lang="ts">
import type { StaffRow } from '@/api/staff'
import type { CellProps } from '@/shared/components/table/types'
import { dictManager } from '@/shared/dict/DictManager'

/** Роли сотрудника, под ними серым — категория проводника. */
defineProps<CellProps<StaffRow>>()
</script>
