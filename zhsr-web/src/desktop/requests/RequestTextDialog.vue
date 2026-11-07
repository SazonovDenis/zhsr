<template>
  <FormDialog v-model:show="show" :title="title" :ok-text="okText" :error="error" @ok="ok">
    <textarea
      v-model="text"
      class="request-text-dialog__textarea q-pa-sm zs-focusable"
      rows="4"
      :placeholder="t(placeholder)"
    />
    <PhotoPicker v-if="photos" v-model="files" />
  </FormDialog>
</template>

<script setup lang="ts">
import { ref } from 'vue'

import { t } from '@/boot/i18n'
import FormDialog from '@/shared/components/dialog/FormDialog.vue'
import type { ShownError } from '@/shared/errors'
import { formError } from '@/shared/errors'
import PhotoPicker from '@/shared/requests/PhotoPicker.vue'

/**
 * Ввод текста к заявке во всплывающем окне: причина действия (отклонить, отменить, вернуть на доработку) или
 * примечание. photos — можно приложить фото. required — без текста нельзя (у причины); иначе нужен текст или фото.
 * Результат — событие done с текстом и фото; что с ними делать, решает вызывающий. Открывается через openDialog.
 */
const props = withDefaults(
  defineProps<{ title: string; placeholder: string; okText: string; photos?: boolean; required?: boolean }>(),
  { photos: false, required: false },
)

const show = defineModel<boolean>('show', { default: false })
const emit = defineEmits<{ done: [result: { text: string; photos: Blob[] }] }>()

const text = ref('')
const files = ref<Blob[]>([])
const error = ref<ShownError | null>(null)

function ok() {
  const value = text.value.trim()
  if (props.required && !value) {
    error.value = formError(t('Укажите причину'))
    return
  }
  if (!value && files.value.length === 0) {
    error.value = formError(t('Напишите примечание или приложите фото'))
    return
  }
  emit('done', { text: value, photos: [...files.value] })
}
</script>

<style scoped lang="scss">
.request-text-dialog__textarea {
  width: 100%;
  border: 0.0625rem solid $zs-border-input;
  border-radius: 0.3rem;
  font: inherit;
  resize: vertical;
}
</style>
