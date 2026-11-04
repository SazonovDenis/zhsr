<template>
  <span class="admin-pager">
    <span>{{ text }}</span>
    <button type="button" class="admin-pager__btn" :disabled="page === 0" :title="t('Назад')" @click="page--">
      <q-icon name="chevron_left" size="1.25rem" />
    </button>
    <button type="button" class="admin-pager__btn" :disabled="last" :title="t('Вперёд')" @click="page++">
      <q-icon name="chevron_right" size="1.25rem" />
    </button>
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
  gap: 0.25rem;
  white-space: nowrap;
}

.admin-pager__btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 1.8rem;
  height: 1.8rem;
  border: 0.0625rem solid $zs-border;
  border-radius: 0.25rem;
  background: #fff;
  color: inherit;
  cursor: pointer;

  &:disabled {
    opacity: 0.4;
    cursor: default;
  }
}
</style>
