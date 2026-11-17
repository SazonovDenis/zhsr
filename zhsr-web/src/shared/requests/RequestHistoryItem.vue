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
        <!-- дата • кто (ФИО разметкой: плашка «вы») и хвост, например «поступило: …» -->
        <span class="history-item__meta">
          {{ meta }}<template v-if="by"> • <UserName :user="by" /></template>{{ metaTail }}
        </span>
      </div>
      <TextPhotos
        v-if="text || textUser || photos.length > 0 || pending.length > 0"
        class="history-item__content"
        :photos="photos"
        :pending="pending"
        :caption="{ meta: captionMeta, text: captionText }"
        :has-text="!!text?.trim() || !!textUser"
      >
        <span v-if="text || textUser" class="history-item__text">
          <!-- пробел — выражением: пробел-разделитель между тегами шаблон при сборке убирает -->
          {{ text }}<template v-if="textUser">{{ ' ' }}<UserName :user="textUser" /></template>
        </span>
        <template v-if="$slots.photos" #photos="scope"><slot name="photos" v-bind="scope" /></template>
      </TextPhotos>
      <slot />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

import type { RequestPhoto, UserRef } from '@/api/requests'
import UserName from '@/shared/components/UserName.vue'
import { userName } from '@/shared/user'
import TextPhotos from './TextPhotos.vue'

/**
 * Запись ленты истории заявки: точка с линией к следующей записи, заголовок и под ним текст и фото (TextPhotos): фото
 * не заходят на заголовок. Слот default — дополнение под текстом, например пометка «Ожидает отправки»; слот photos —
 * фото, ожидающие отправки (pending). line — линия к следующей записи: обычная, серая или её нет. inline — meta в одной
 * строке с заголовком, сразу после него. meta — дата; by — кто (после даты через «•»); metaTail — хвост после него.
 * textUser — сотрудник после текста (например, «Исполнитель:» и ФИО). ФИО — разметкой (UserName); в подписи к фото —
 * текстом.
 */
const props = withDefaults(
  defineProps<{
    title: string
    meta: string
    by?: UserRef | null
    metaTail?: string
    text?: string
    textUser?: UserRef | null
    photos?: RequestPhoto[]
    pending?: RequestPhoto[]
    inline?: boolean
    future?: boolean
    line?: 'none' | 'solid' | 'future'
  }>(),
  {
    by: null,
    metaTail: '',
    text: '',
    textUser: null,
    photos: () => [],
    pending: () => [],
    inline: false,
    future: false,
    line: 'none',
  },
)

// подпись к фото в просмотре — обычным текстом
const captionMeta = computed(() => `${props.meta}${props.by ? ` • ${userName(props.by)}` : ''}${props.metaTail}`)
const captionText = computed(() => (props.textUser ? `${props.text} ${userName(props.textUser)}` : props.text))
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
  border: 0.1rem solid $zs-progress-done;
  border-radius: 50%;
  background: $zs-progress-done;

  &--future {
    border-color: $zs-border;
    background: #fff;
  }
}

.history-item__line {
  flex: 1;
  width: 0.1rem;
  background: $zs-progress-done;

  // к предстоящим шагам — серым пунктиром
  &--future {
    width: 0;
    border-left: 0.1rem dashed $zs-border;
    background: none;
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
