<template>
  <span class="admin-pager">
    <span>{{ text }}</span>
    <q-btn
      unelevated
      outline
      dense
      color="ink"
      class="zs-soft-border"
      icon="chevron_left"
      :disable="page === 0"
      :title="t('Назад')"
      @click="page--"
    />
    <q-btn
      unelevated
      outline
      dense
      color="ink"
      class="zs-soft-border"
      icon="chevron_right"
      :disable="last"
      :title="t('Вперёд')"
      @click="page++"
    />
  </span>
</template>

<script setup lang="ts">
import { computed } from 'vue'

import { t } from '@/boot/i18n'

/**
 * Страницы списка: «51–100 из 230» и кнопки назад и вперёд. v-model — номер страницы с 0.
 */
const props = defineProps<{ total: number; size: number }>()

const page = defineModel<number>({ required: true })

const last = computed(() => (page.value + 1) * props.size >= props.total)

const text = computed(() => {
  if (props.total === 0) return t('Нет записей')
  const from = page.value * props.size + 1
  const to = Math.min(props.total, (page.value + 1) * props.size)
  return t('{from}–{to} из {total}', { from, to, total: props.total })
})
</script>

<style scoped lang="scss">
.admin-pager {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  white-space: nowrap;
}
</style>
