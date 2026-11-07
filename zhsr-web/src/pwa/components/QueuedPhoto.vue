<template>
  <div class="queued-photo">
    <PhotoStrip v-if="photo" :photos="[photo]" :all="all" :caption="caption" small />
    <QueuedRefusal v-if="item.refused" :item="item" />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

import type { RequestPhoto } from '@/api/requests'
import type { OutboxItem } from '@/shared/offline/outbox'
import PhotoStrip from '@/shared/requests/PhotoStrip.vue'
import type { PhotoCaption } from '@/shared/requests/requestView'
import QueuedRefusal from './QueuedRefusal.vue'

/**
 * Фото из очереди: миниатюра и, если сервер отказал, надпись «Не принято». all — все фото группы для листания в
 * просмотре; среди них и это фото, его адрес берётся оттуда.
 */
const props = defineProps<{ item: OutboxItem; all: RequestPhoto[]; caption: PhotoCaption | null }>()

const photo = computed(() => props.all.find((x) => x.id === props.item.id))
</script>

<style scoped lang="scss">
.queued-photo {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 0.3rem;
}
</style>
