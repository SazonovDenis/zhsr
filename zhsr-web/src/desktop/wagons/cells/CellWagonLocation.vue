<template>
  <div>
    {{ wagonPlace(row) }}<span class="zs-sub"> • {{ dictManager.getValue('LocationKind', row.locationType) }}</span>
  </div>
  <div class="zs-sub">{{ t('с {date}', { date: formatDateTime(row.dbegLocation) }) }}</div>
</template>

<script setup lang="ts">
import type { WagonRow } from '@/api/wagons'
import { t } from '@/boot/i18n'
import type { CellProps } from '@/shared/components/table/types'
import { dictManager } from '@/shared/dict/DictManager'
import { formatDateTime } from '@/shared/format'
import { wagonPlace } from '@/shared/wagonPlace'

/** Расположение вагона: место, вид расположения и с какого времени. */
defineProps<CellProps<WagonRow>>()
</script>
