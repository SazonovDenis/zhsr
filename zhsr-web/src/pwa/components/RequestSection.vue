<template>
  <section class="request-section">
    <div class="request-section__head">
      <span>{{ title }}</span>
      <span class="request-section__count">{{ requests.length + queued.length }}</span>
    </div>
    <div class="request-section__card">
      <RequestRow v-for="r in queued" :key="r.id" :request="r" queued />
      <RequestRow v-for="r in requests" :key="r.id" :request="r" />
      <div v-if="requests.length + queued.length === 0" class="request-section__empty">{{ empty }}</div>
    </div>
  </section>
</template>

<script setup lang="ts">
import type { RepairRequest } from '@/api/requests'
import RequestRow from './RequestRow.vue'

/**
 * Раздел со списком заявок на экранах мобильной части: заголовок с числом заявок и карточка со строками; пустой
 * раздел — текст empty. queued — заявки из очереди на телефоне, показываются первыми. Тексты — уже переведённые.
 */
withDefaults(defineProps<{ title: string; requests: RepairRequest[]; queued?: RepairRequest[]; empty: string }>(), {
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
  display: flex;
  align-items: center;
  gap: 0.5rem;
  color: $zs-text-muted;
  font-size: 0.8125rem;
  font-weight: 700;
  letter-spacing: 0.05em;
  text-transform: uppercase;
}

.request-section__count {
  min-width: 1.4rem;
  height: 1.4rem;
  padding: 0 0.375rem;
  border-radius: 0.6875rem;
  background: $zs-selected-bg;
  color: $zs-text;
  font-size: 0.75rem;
  line-height: 1.4rem;
  text-align: center;
}

.request-section__card {
  overflow: hidden;
  border: 0.0625rem solid $zs-border;
  border-radius: 0.375rem;
  background: #fff;
}

.request-section__empty {
  padding: 1.125rem 1rem;
  color: $zs-text-muted;
  font-size: 0.875rem;
}
</style>
