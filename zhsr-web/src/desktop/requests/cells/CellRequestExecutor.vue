<template>
  <div>{{ dictManager.getValue('Service', row.service) }}</div>
  <div class="zs-sub">
    <UserName v-if="row.executorUsr" :user="row.executorUsr" /><template v-else>{{ t('не назначен') }}</template>
  </div>
</template>

<script setup lang="ts">
import type { RepairRequest } from '@/api/requests'
import { t } from '@/boot/i18n'
import type { CellProps } from '@/shared/components/table/types'
import { dictManager } from '@/shared/dict/DictManager'

import UserName from '@/shared/components/UserName.vue'
/** Служба-исполнитель и назначенный исполнитель. */
defineProps<CellProps<RepairRequest>>()
</script>
