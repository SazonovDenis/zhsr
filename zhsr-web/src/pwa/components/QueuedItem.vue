<template>
  <RequestHistoryItem :title="title" :meta="meta" :text="text" :pending="pending" :line="line" inline>
    <div class="queued-item__marks q-mt-xs">
      <span v-if="waiting" class="zs-badge queued-item__badge">{{ t('Ожидает отправки') }}</span>
      <template v-for="item in records" :key="item.id">
        <QueuedRefusal v-if="item.refused" :item="item" />
      </template>
    </div>
    <template v-if="photos.length > 0" #photos="{ all, caption }">
      <QueuedPhoto v-for="item in photos" :key="item.id" :item="item" :all="all" :caption="caption" />
    </template>
  </RequestHistoryItem>
</template>

<script setup lang="ts">
import { computed } from 'vue'

import type { RequestAction } from '@/api/requests'
import { t } from '@/boot/i18n'
import { formatDateTime } from '@/shared/format'
import type { OutboxItem } from '@/shared/offline/outbox'
import RequestHistoryItem from '@/shared/requests/RequestHistoryItem.vue'
import { ACTIONS } from '@/shared/requests/rights'
import { currentUser, userName } from '@/shared/user'
import QueuedPhoto from './QueuedPhoto.vue'
import QueuedRefusal from './QueuedRefusal.vue'
import { useQueuedPhotos } from './useQueuedPhotos'

/**
 * Группа записей очереди с одним временем — запись истории заявки, как обычное примечание, и пометка, что она ждёт
 * отправки. Записи независимы, поэтому «Не принято» у каждой своё.
 */
const props = withDefaults(defineProps<{ items: OutboxItem[]; line?: 'none' | 'solid' | 'future' }>(), {
  line: 'none',
})

const records = computed(() => props.items.filter((x) => x.kind !== 'photo'))
const photos = computed(() => props.items.filter((x) => x.kind === 'photo'))
const pending = useQueuedPhotos(() => photos.value)
const waiting = computed(() => props.items.some((x) => !x.refused))

const title = computed(() => {
  const action = records.value.find((x) => x.kind === 'action')
  if (action) return t(ACTIONS[action.action as RequestAction].title)
  const assign = records.value.find((x) => x.kind === 'assign')
  if (assign) return assign.executor ? `${t('Назначение')}: ${assign.text}` : t('Назначение снято')
  if (records.value.some((x) => x.kind === 'edit')) return t('Исправление заявки')
  return t(records.value.length > 0 ? 'Примечание' : 'Приложено фото')
})
const meta = computed(
  () => `${formatDateTime(props.items[0]!.created)} • ${currentUser.value ? userName(currentUser.value) : ''}`,
)
const text = computed(() => records.value.find((x) => x.kind === 'note' || x.kind === 'action')?.text ?? '')
</script>

<style scoped lang="scss">
.queued-item__marks {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.5rem;
}

.queued-item__badge {
  background: #fff1cc;
  color: #6b4700;
}
</style>
