<template>
  <div class="history-item">
    <div class="history-item__rail">
      <span class="history-item__dot q-mt-xs" :class="{ 'history-item__dot--future': future }" />
      <span
        v-if="line !== 'none'"
        class="history-item__line"
        :class="{ 'history-item__line--future': line === 'future' }"
      />
    </div>
    <div class="history-item__body q-pb-md" :class="{ 'history-item__body--future': future }">
      <div class="history-item__head" :class="{ 'history-item__head--inline': inline }">
        <span class="history-item__title">{{ title }}</span>
        <span class="history-item__meta">{{ meta }}</span>
      </div>
      <TextPhotos
        v-if="text || photos.length > 0 || pending.length > 0"
        class="history-item__content"
        :photos="photos"
        :pending="pending"
        :caption="{ meta, text }"
      >
        <span v-if="text" class="history-item__text">{{ text }}</span>
        <template v-if="$slots.photos" #photos="scope"><slot name="photos" v-bind="scope" /></template>
      </TextPhotos>
      <slot />
    </div>
  </div>
</template>

<script setup lang="ts">
import type { RequestPhoto } from '@/api/requests'
import TextPhotos from './TextPhotos.vue'

/**
 * Запись ленты истории заявки: точка с линией к следующей записи, заголовок и под ним текст и фото (TextPhotos): фото
 * не заходят на заголовок. Слот default — дополнение под текстом, например пометка «Ожидает отправки»; слот photos —
 * фото, ожидающие отправки (pending). line — линия к следующей записи: обычная, серая или её нет. inline — meta в одной
 * строке с заголовком, сразу после него.
 */
withDefaults(
  defineProps<{
    title: string
    meta: string
    text?: string
    photos?: RequestPhoto[]
    pending?: RequestPhoto[]
    inline?: boolean
    future?: boolean
    line?: 'none' | 'solid' | 'future'
  }>(),
  { text: '', photos: () => [], pending: () => [], inline: false, future: false, line: 'none' },
)
</script>

<style scoped lang="scss">
.history-item {
  display: flex;
  gap: 0.8rem;
}

.history-item__rail {
  display: flex;
  flex: none;
  flex-direction: column;
  align-items: center;
  width: 0.6rem;
}

.history-item__dot {
  flex: none;
  width: 0.6rem;
  height: 0.6rem;
  border: 0.1rem solid $primary;
  border-radius: 50%;
  background: $primary;

  &--future {
    border-color: $zs-border;
    background: #fff;
  }
}

.history-item__line {
  flex: 1;
  width: 0.1rem;
  background: $primary;

  &--future {
    background: $zs-border;
  }
}

.history-item__body {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 0.1rem;
  min-width: 0;

  &--future {
    color: $zs-text-muted;
  }
}

.history-item__head {
  display: flex;
  flex-direction: column;
  gap: 0.1rem;

  // одним абзацем: длинная строка переносится словами, а не целиком под заголовок
  &--inline {
    display: block;

    .history-item__meta {
      margin-left: 0.5rem;
    }
  }
}

.history-item__content {
  margin-top: 0.1rem;
}

.history-item__title {
  font-weight: 600;
}

.history-item__meta {
  color: $zs-text-muted;
  font-size: 0.8rem;
}

.history-item__text {
  line-height: 1.4;
}
</style>
