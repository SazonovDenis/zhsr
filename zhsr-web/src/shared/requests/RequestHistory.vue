<template>
  <div class="request-history" :class="{ 'request-history--large': large }">
    <div v-for="(item, i) in items" :key="i" class="request-history__item">
      <div class="request-history__rail">
        <span class="request-history__dot" :class="{ 'request-history__dot--future': item.future }" />
        <span
          v-if="i < items.length - 1"
          class="request-history__line"
          :class="{ 'request-history__line--future': items[i + 1]!.future }"
        />
      </div>
      <div class="request-history__body" :class="{ 'request-history__body--future': item.future }">
        <span class="request-history__title">{{ item.title }}</span>
        <span class="request-history__meta">{{ item.meta }}</span>
        <span v-if="item.text" class="request-history__text">«{{ item.text }}»</span>
        <PhotoStrip v-if="item.photos.length > 0" :photos="item.photos" small />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

import type { RequestEvent, RequestPhoto } from '@/api/requests'
import { t } from '@/boot/i18n'
import { formatDateTime } from '@/shared/format'
import PhotoStrip from './PhotoStrip.vue'
import { eventTitle, futureSteps } from './requestView'

/**
 * История заявки лентой: подача, смены статуса с комментариями, назначения, пояснения с фото; в конце серым — шаги,
 * которые заявке ещё предстоят. large — крупнее, для телефона.
 */
const props = withDefaults(defineProps<{ events: RequestEvent[]; status: number; large?: boolean }>(), {
  large: false,
})

interface Item {
  title: string
  meta: string
  text: string
  photos: RequestPhoto[]
  future: boolean
}

const items = computed<Item[]>(() => [
  ...props.events.map((e) => ({
    title: eventTitle(e),
    meta: `${e.by?.name ?? t('Система')} • ${formatDateTime(e.at)}`,
    text: e.text ?? '',
    photos: e.photos ?? [],
    future: false,
  })),
  ...futureSteps(props.status).map((s) => ({ title: s.title, meta: s.meta, text: '', photos: [], future: true })),
])
</script>

<style scoped lang="scss">
.request-history {
  display: flex;
  flex-direction: column;
  font-size: 0.8125rem;
}

.request-history__item {
  display: flex;
  gap: 0.75rem;
}

.request-history__rail {
  display: flex;
  flex: none;
  flex-direction: column;
  align-items: center;
  width: 0.6rem;
}

.request-history__dot {
  flex: none;
  width: 0.6rem;
  height: 0.6rem;
  margin-top: 0.25rem;
  border: 0.125rem solid $primary;
  border-radius: 50%;
  background: $primary;

  &--future {
    border-color: $zs-border;
    background: #fff;
  }
}

.request-history__line {
  flex: 1;
  width: 0.125rem;
  background: $primary;

  &--future {
    background: $zs-border;
  }
}

.request-history__body {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 0.125rem;
  min-width: 0;
  padding-bottom: 0.75rem;

  &--future {
    color: $zs-text-muted;
  }
}

.request-history__title {
  font-weight: 600;
}

.request-history__meta {
  color: $zs-text-muted;
  font-size: 0.75rem;
}

.request-history__text {
  line-height: 1.4;
}

.request-history--large {
  font-size: 0.875rem;
}
</style>
