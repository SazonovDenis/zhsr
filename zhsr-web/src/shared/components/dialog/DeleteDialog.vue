<template>
  <FormDialog
    v-model:show="show"
    height="sm"
    :title="title"
    ok-text="Удалить"
    :running="running"
    :error="error"
    @ok="run"
  >
    <div>{{ t(text, params ?? {}) }}</div>
  </FormDialog>
</template>

<script setup lang="ts">
import { ref } from 'vue'

import { t } from '@/boot/i18n'
import FormDialog from './FormDialog.vue'
import type { ShownError } from '@/shared/errors'
import { shownError } from '@/shared/errors'

/**
 * Подтверждение удаления записи. Открывается через openDialog, результат — true, если удалено. Удалить нельзя (на запись
 * ссылаются) — причина от сервера в окне, окно остаётся открытым. Заголовок и текст — на русском, переводятся при
 * показе.
 *
 *   openDialog<boolean>(DeleteDialog, { title: 'Удаление маршрута', text: 'Удалить маршрут {name}?',
 *     params: { name }, remove: () => deleteRoute(id) })
 */
const props = defineProps<{
  title: string
  text: string
  params?: Record<string, unknown>
  remove: () => Promise<void>
}>()

const show = defineModel<boolean>('show', { default: false })
const emit = defineEmits<{ done: [removed: boolean] }>()

const running = ref(false)
const error = ref<ShownError | null>(null)

async function run() {
  error.value = null
  running.value = true
  try {
    await props.remove()
    emit('done', true)
  } catch (e) {
    error.value = shownError(e)
  } finally {
    running.value = false
  }
}
</script>
