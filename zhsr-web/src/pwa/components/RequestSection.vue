<template>
  <section class="request-section">
    <div class="request-section__head">
      <span>{{ title }}</span>
      <span class="request-section__count q-py-none q-px-sm">{{ requests.length + queued.length }}</span>
    </div>
    <div class="request-section__card">
      <RequestRow v-for="r in queued" :key="r.id" :request="r" queued />
      <RequestRow v-for="r in requests" :key="r.id" :request="r" />
      <div v-if="requests.length + queued.length === 0" class="request-section__empty q-pa-md">{{ empty }}</div>
    </div>
  </section>
</template>

<script setup lang="ts">
import type { RepairRequest } from '@/api/requests'
import type { QueuedRequest } from '@/shared/offline/outbox'
import RequestRow from './RequestRow.vue'

/**
 * Раздел со списком заявок на экранах мобильной части: заголовок с числом заявок и карточка со строками; пустой
 * раздел — текст empty. queued — заявки из очереди на телефоне, показываются первыми. Тексты — уже переведённые.
 */
withDefaults(defineProps<{ title: string; requests: RepairRequest[]; queued?: QueuedRequest[]; empty: string }>(), {
  queued: () => [],
})
</script>

<style scoped lang="scss">
.request-section {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.request-section__head {
  // отступ под подписью группы — на треть меньше промежутка между блоками (0.5rem)
  margin-bottom: calc(-0.5rem / 3);
  display: flex;
  align-items: center;
  gap: 0.5rem;
  color: $zs-text-muted;
  font-size: 0.8rem;
  font-weight: 700;
  letter-spacing: 0.1em;
  text-transform: uppercase;
}

.request-section__count {
  min-width: 1.4rem;
  height: 1.4rem;
  border-radius: 0.7rem;
  background: $zs-selected-bg;
  color: $zs-text;
  font-size: 0.8rem;
  line-height: 1.4rem;
  text-align: center;
}

.request-section__card {
  overflow: hidden;
  border: 0.0625rem solid $zs-border;
  border-radius: 0.4rem;
  background: #fff;
}

.request-section__empty {
  color: $zs-text-muted;
  font-size: 0.9rem;
}
</style>
