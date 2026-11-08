<template>
  <DialogFrame v-model:show="show" :title="t('Заявка назначена другому')" width="30rem" :actions="actions">
    <div>{{ t('Исполнитель заявки — {name}. Взять её в работу вместо него?', { name }) }}</div>
  </DialogFrame>
</template>

<script setup lang="ts">
import { t } from '@/boot/i18n'
import DialogFrame from '@/shared/components/dialog/DialogFrame.vue'
import type { DialogAction } from '@/shared/components/dialog/types'

/**
 * Подтверждение: взять в работу заявку, у которой исполнитель — другой сотрудник. Подтверждение — событие done(true);
 * закрытие окна отменяет действие. Открывается через openDialog.
 */
defineProps<{ name: string }>()

const emit = defineEmits<{ done: [confirmed: boolean] }>()

const show = defineModel<boolean>('show', { default: false })

const actions: DialogAction[] = [
  { title: 'Отмена', left: true },
  { title: 'Взять в работу', default: true, exec: () => emit('done', true) },
]
</script>
