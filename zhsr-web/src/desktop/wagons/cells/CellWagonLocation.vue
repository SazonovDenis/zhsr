<template>
  <!-- основным — вид расположения и сколько вагон в нём, серым — само место -->
  <div>{{ dictManager.getValue('LocationKind', row.locationType) }}, {{ sinceText(row.dbegLocation, nowMinute) }}</div>
  <div class="zs-sub">{{ wagonPlace(row) }}</div>
  <div class="zs-sub">
    {{ t('ответственный: {name}', { name: row.responsibleUsr ? userName(row.responsibleUsr) : t('не назначен') }) }}
  </div>
</template>

<script setup lang="ts">
import type { WagonRow } from '@/api/wagons'
import { t } from '@/boot/i18n'
import type { CellProps } from '@/shared/components/table/types'
import { nowMinute, sinceText } from '@/shared/since'
import { dictManager } from '@/shared/dict/DictManager'
import { userName } from '@/shared/user'
import { wagonPlace } from '@/shared/wagonPlace'

/**
 * Расположение вагона: вид расположения, с какого времени и сколько уже длится; второй строкой — место, третьей —
 * текущий ответственный.
 */
defineProps<CellProps<WagonRow>>()
</script>
