<template>
  <FormDialog v-model:show="show" :title="title" :ok-text="okText" :cancel-text="cancelText" :error="error" @ok="ok">
    <NoteInput v-model="text" :rows="4" :placeholder="placeholder" />
    <PhotoPicker v-if="photos" v-model="files" />
  </FormDialog>
</template>

<script setup lang="ts">
import { ref } from 'vue'

import { t } from '@/boot/i18n'
import NoteInput from '@/shared/components/form/NoteInput.vue'
import FormDialog from '@/shared/components/dialog/FormDialog.vue'
import type { ShownError } from '@/shared/errors'
import { formError } from '@/shared/errors'
import PhotoPicker from '@/shared/requests/PhotoPicker.vue'

/**
 * Ввод текста к заявке во всплывающем окне: причина действия (отклонить, отменить, вернуть на доработку) или
 * примечание. photos — можно приложить фото. required — без текста нельзя (у причины); иначе нужен текст или фото.
 * Результат — событие done с текстом и фото; что с ними делать, решает вызывающий. Открывается через openDialog.
 * initial — текст в поле при открытии (исправление примечания); requiredText — отказ при пустом тексте, если required.
 */
const props = withDefaults(
  defineProps<{
    title: string
    placeholder: string
    okText: string
    /** текст кнопки «Отмена» на русском */
    cancelText?: string
    photos?: boolean
    required?: boolean
    initial?: string
    requiredText?: string
  }>(),
  { cancelText: 'Отмена', photos: false, required: false, initial: '', requiredText: 'Укажите причину' },
)

const show = defineModel<boolean>('show', { default: false })
const emit = defineEmits<{ done: [result: { text: string; photos: Blob[] }] }>()

const text = ref(props.initial)
const files = ref<Blob[]>([])
const error = ref<ShownError | null>(null)

function ok() {
  const value = text.value.trim()
  if (props.required && !value) {
    error.value = formError(t(props.requiredText))
    return
  }
  if (!value && files.value.length === 0) {
    error.value = formError(t('Напишите примечание или приложите фото'))
    return
  }
  emit('done', { text: value, photos: [...files.value] })
}
</script>

<style scoped lang="scss"></style>
