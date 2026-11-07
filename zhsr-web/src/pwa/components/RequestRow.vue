<template>
  <router-link :to="to" class="request-row q-pa-md" :class="{ 'request-row--queued': queued }">
    <div class="request-row__main">
      <div class="request-row__title">{{ faultName(request.faultType) }}</div>
      <div class="request-row__meta">
        {{ queued ? t('Новая') : request.number }} • {{ t('ваг. {n}', { n: request.wagon.number }) }} •
        {{ formatDateTime(request.createdDt) }}
      </div>
      <div class="request-row__badges">
        <DictBadge v-if="!queued" :id="request.requestStatusType" dict="RequestStatus" />
        <span :style="severityStyle(request.severityType)">{{
          dictManager.getValue('Severity', request.severityType)
        }}</span>
        <span class="request-row__to">→ {{ executorText(request) }}</span>
      </div>
      <div v-if="unsent.length > 0" class="request-row__unsent">
        <q-icon name="warning" size="1rem" class="request-row__unsent-icon" />
        <span v-for="text in unsent" :key="text">{{ text }}</span>
      </div>
      <div v-if="refused > 0" class="request-row__refused">{{ t('Не принято: {n}', { n: refused }) }}</div>
    </div>
    <span class="request-row__arrow">›</span>
  </router-link>
</template>

<script setup lang="ts">
import { computed } from 'vue'

import type { RepairRequest } from '@/api/requests'
import type { QueuedRequest } from '@/shared/offline/outbox'
import { t } from '@/boot/i18n'
import type { OutboxKind } from '@/shared/db/db'
import DictBadge from '@/shared/components/DictBadge.vue'
import { dictManager } from '@/shared/dict/DictManager'
import { formatDateTime } from '@/shared/format'
import { queuedFor } from '@/shared/offline/outbox'
import { executorText, faultName, severityStyle } from '@/shared/requests/requestView'

/**
 * Строка заявки в списках мобильной части: неисправность, номер, вагон, время подачи, статус, критичность и кому
 * заявка. Заявка из очереди на телефоне (queued) — номера и статуса ещё нет. Что по заявке не отправлено — жёлтой
 * иконкой и надписью; чему сервер отказал — числом «Не принято».
 */
const props = withDefaults(defineProps<{ request: RepairRequest | QueuedRequest; queued?: boolean }>(), {
  queued: false,
})

// что ждёт отправки к этой заявке
const items = computed(() => queuedFor('guid' in props.request ? props.request.guid : props.request.id))

const unsent = computed(() => {
  const count = (kind: OutboxKind) => items.value.filter((x) => x.kind === kind).length
  const notes = count('note')
  const photos = count('photo')
  return [
    ...(props.queued ? [t('Не отправлена')] : []),
    ...(count('edit') > 0 ? [t('Не отправлена правка')] : []),
    ...(notes > 0 ? [t('Не отправлено примечаний: {n}', { n: notes })] : []),
    ...(photos > 0 ? [t('Не отправлено фото: {n}', { n: photos })] : []),
  ]
})

const refused = computed(() => {
  const self = 'refused' in props.request && props.request.refused ? 1 : 0
  return self + items.value.filter((x) => x.refused).length
})

const to = computed(() => ({ name: 'pwa-request', params: { id: props.request.id } }))
</script>

<style scoped lang="scss">
.request-row {
  display: flex;
  align-items: center;
  gap: 0.8rem;
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
  gap: 0.3rem;
  min-width: 0;
}

.request-row__title {
  font-size: 0.9rem;
  font-weight: 600;
  line-height: 1.3;
}

.request-row__meta {
  color: $zs-text-muted;
  font-size: 0.8rem;
  font-variant-numeric: tabular-nums;
}

.request-row__badges {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.8rem;
}

.request-row__unsent {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.3rem 0.5rem;
  margin-top: 0.3rem;
  color: #7a5200;
  font-size: 0.8rem;
  font-weight: 600;
}

.request-row__unsent-icon {
  color: #f2a516;
}

.request-row__refused {
  margin-top: 0.3rem;
  color: $zs-error-fg;
  font-size: 0.8rem;
  font-weight: 600;
}

.request-row__to {
  color: $zs-text-muted;
}

.request-row__arrow {
  color: $zs-text-muted;
  font-size: 1.1rem;
}
</style>
