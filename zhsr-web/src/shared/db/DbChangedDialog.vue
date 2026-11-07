<template>
  <DialogFrame v-model:show="show" :title="t('База данных сервера изменилась')" width="30rem" :actions="actions">
    <div>{{ t('Сервер сменил базу данных. Неотправленные заявки будут удалены: {n}.', { n: count }) }}</div>
  </DialogFrame>
</template>

<script setup lang="ts">
import { t } from '@/boot/i18n'
import DialogFrame from '@/shared/components/dialog/DialogFrame.vue'
import type { DialogAction } from '@/shared/components/dialog/types'

/**
 * Предупреждение перед стиранием локальной БД: неотправленные заявки отправить некуда. Отмены нет: и «ОК», и закрытие
 * окна только продолжают стирание. Открывается через openDialog.
 */
defineProps<{ count: number }>()

const show = defineModel<boolean>('show', { default: false })

const actions: DialogAction[] = [{ title: 'ОК', default: true }]
</script>
