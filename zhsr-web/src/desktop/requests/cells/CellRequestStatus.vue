<template>
  <DictBadge :id="row.status" dict="RequestStatus" class="cell-request-status" />
  <div class="cell-request-status__sev" :style="severityStyle(row.severity)">
    {{ dictManager.getValue('Severity', row.severity) }}
  </div>
</template>

<script setup lang="ts">
import type { RepairRequest } from '@/api/requests'
import DictBadge from '@/shared/components/DictBadge.vue'
import type { CellProps } from '@/shared/components/table/types'
import { dictManager } from '@/shared/dict/DictManager'
import { severityStyle } from '@/shared/requests/requestView'

/** Статус плашкой и критичность цветом под ним. */
defineProps<CellProps<RepairRequest>>()
</script>

<style scoped lang="scss">
// как у состояния вагона: плашка выступает влево на свой отступ
.cell-request-status {
  margin: 0 0 0.1875rem -0.5rem;
}

.cell-request-status__sev {
  font-size: 0.78125rem;
}
</style>
