<template>
  <div>
    {{ row.location.name }}<span class="zs-sub"> • {{ dictManager.getValue('LocationKind', row.location.kind) }}</span>
  </div>
  <div class="zs-sub">{{ t('с {date}', { date: formatDateTime(row.location.since) }) }}</div>
</template>

<script setup lang="ts">
import type { Wagon } from '@/api/wagons'
import { t } from '@/boot/i18n'
import type { CellProps } from '@/shared/components/table/types'
import { dictManager } from '@/shared/dict/DictManager'
import { formatDateTime } from '@/shared/format'

/** Расположение вагона: место, вид расположения и с какого времени. */
defineProps<CellProps<Wagon>>()
</script>
