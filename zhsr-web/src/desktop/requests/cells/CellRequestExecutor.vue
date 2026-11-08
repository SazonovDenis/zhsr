<template>
  <div>{{ dictManager.getValue('Service', row.service) }}</div>
  <div class="zs-sub">{{ row.executorUsr ? userName(row.executorUsr) : t('не назначен') }}</div>
</template>

<script setup lang="ts">
import type { RepairRequest } from '@/api/requests'
import { t } from '@/boot/i18n'
import type { CellProps } from '@/shared/components/table/types'
import { dictManager } from '@/shared/dict/DictManager'
import { userName } from '@/shared/user'

/** Служба-исполнитель и назначенный исполнитель. */
defineProps<CellProps<RepairRequest>>()
</script>
