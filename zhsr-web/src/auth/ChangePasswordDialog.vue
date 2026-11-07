<template>
  <FormDialog
    v-model:show="show"
    title="Смена пароля"
    ok-text="Сменить пароль"
    width="26rem"
    hint="Пароль — не короче 6 символов."
    :running="running"
    :error="error"
    @ok="submit"
  >
    <FormField label="Текущий пароль">
      <TextInput v-model="oldPassword" password />
    </FormField>
    <FormField label="Новый пароль">
      <TextInput v-model="newPassword" password />
    </FormField>
    <FormField label="Новый пароль ещё раз">
      <TextInput v-model="repeatPassword" password />
    </FormField>
  </FormDialog>
</template>

<script setup lang="ts">
import { Notify } from 'quasar'

import { t } from '@/boot/i18n'
import FormDialog from '@/shared/components/dialog/FormDialog.vue'
import FormField from '@/shared/components/form/FormField.vue'
import TextInput from '@/shared/components/form/TextInput.vue'
import { usePasswordChange } from './usePasswordChange'

/** Смена своего пароля из меню пользователя. Открывается через openDialog, результат — true. */
const show = defineModel<boolean>('show', { default: false })
const emit = defineEmits<{ done: [changed: boolean] }>()

const { oldPassword, newPassword, repeatPassword, running, error, save } = usePasswordChange()

async function submit() {
  if (!(await save())) return
  Notify.create({ message: t('Пароль изменён'), position: 'bottom' })
  emit('done', true)
}
</script>
