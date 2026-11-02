<template>
  <router-link :to="to" class="request-row" :class="{ 'request-row--queued': queued }">
    <div class="request-row__main">
      <div class="request-row__title">{{ faultName(request.fault) }}</div>
      <div class="request-row__meta">
        {{ queued ? t('номер присвоят при отправке') : request.number }} • {{ t('ваг. {n}', { n: request.wagon }) }} •
        {{ formatDateTime(request.created) }}
      </div>
      <div class="request-row__badges">
        <span v-if="queued" class="zs-badge request-row__queued">{{ t('Ожидает отправки') }}</span>
        <DictBadge v-else :id="request.status" dict="RequestStatus" />
        <span :style="severityStyle(request.severity)">{{ dictManager.getValue('Severity', request.severity) }}</span>
        <span class="request-row__to">→ {{ executorText(request) }}</span>
      </div>
    </div>
    <span class="request-row__arrow">›</span>
  </router-link>
</template>

<script setup lang="ts">
import { computed } from 'vue'

import type { RepairRequest } from '@/api/requests'
import { t } from '@/boot/i18n'
import DictBadge from '@/shared/components/DictBadge.vue'
import { dictManager } from '@/shared/dict/DictManager'
import { formatDateTime } from '@/shared/format'
import { executorText, faultName, severityStyle } from '@/shared/requests/requestView'

/**
 * Строка заявки в списках мобильной части: неисправность, номер, вагон, время подачи, статус, критичность и кому
 * заявка. Заявка из очереди на телефоне (queued) — «Ожидает отправки», номера ещё нет.
 */
const props = withDefaults(defineProps<{ request: RepairRequest; queued?: boolean }>(), { queued: false })

const to = computed(() => ({ name: 'pwa-request', params: { id: props.request.id } }))
</script>

<style scoped lang="scss">
.request-row {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.875rem 1rem;
  border-top: 0.0625rem solid $zs-border-row;
  color: inherit;
  text-decoration: none;

  &:first-child {
    border-top: 0;
  }

  &:active {
    background: $zs-bg-hover;
  }
}

.request-row__main {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 0.25rem;
  min-width: 0;
}

.request-row__title {
  font-size: 0.9375rem;
  font-weight: 600;
  line-height: 1.3;
}

.request-row__meta {
  color: $zs-text-muted;
  font-size: 0.8125rem;
  font-variant-numeric: tabular-nums;
}

.request-row__badges {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.75rem;
}

.request-row__queued {
  background: #fff1cc;
  color: #6b4700;
}

.request-row__to {
  color: $zs-text-muted;
}

.request-row__arrow {
  color: $zs-text-muted;
  font-size: 1.125rem;
}
</style>
