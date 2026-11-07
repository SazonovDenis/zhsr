<template>
  <DialogFrame v-model:show="show" :title="t('Неотправленные записи будут удалены')" width="30rem" :actions="actions">
    <div>{{ text }}</div>
  </DialogFrame>
</template>

<script setup lang="ts">
import { t } from '@/boot/i18n'
import DialogFrame from '@/shared/components/dialog/DialogFrame.vue'
import type { DialogAction } from '@/shared/components/dialog/types'

/**
 * Подтверждение входа другого пользователя: записи прежнего пользователя отправить под чужим входом нельзя, они будут
 * удалены. Подтверждение — событие done(true); закрытие окна отменяет вход. Открывается через openDialog.
 */
const props = defineProps<{ login: string; count: number }>()

const text = t('На телефоне есть неотправленные записи пользователя {login}: {n}. Войти другим и удалить их?', {
  login: props.login,
  n: props.count,
})

const emit = defineEmits<{ done: [confirmed: boolean] }>()

const show = defineModel<boolean>('show', { default: false })

const actions: DialogAction[] = [
  { title: 'Отмена' },
  { title: 'Удалить и войти', default: true, exec: () => emit('done', true) },
]
</script>
