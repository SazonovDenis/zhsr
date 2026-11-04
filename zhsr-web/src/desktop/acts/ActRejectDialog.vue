<template>
  <FormDialog
    v-model:show="show"
    title="Отказ в приёме"
    ok-text="Отказать"
    :running="running"
    :error="error"
    @ok="save"
  >
    <FormField label="Причина">
      <textarea v-model="reason" class="act-reject__input" rows="4" />
    </FormField>
  </FormDialog>
</template>

<script setup lang="ts">
import { ref } from 'vue'

import type { Transfer } from '@/api/transfers'
import { rejectTransfer } from '@/api/transfers'
import { t } from '@/boot/i18n'
import FormDialog from '@/shared/components/dialog/FormDialog.vue'
import FormField from '@/shared/components/form/FormField.vue'
import type { ShownError } from '@/shared/errors'
import { formError, shownError } from '@/shared/errors'

/** Отказ в приёме вагонов с причиной (Ф-АКТ-02). Открывается через openDialog, результат — акт после отказа. */
const props = defineProps<{ actId: number }>()

const show = defineModel<boolean>('show', { default: false })
const emit = defineEmits<{ done: [act: Transfer] }>()

const reason = ref('')
const running = ref(false)
const error = ref<ShownError | null>(null)

async function save() {
  if (!reason.value.trim()) {
    error.value = formError(t('Укажите причину отказа'))
    return
  }
  error.value = null
  running.value = true
  try {
    emit('done', await rejectTransfer(props.actId, reason.value))
  } catch (e) {
    error.value = shownError(e)
  } finally {
    running.value = false
  }
}
</script>

<style scoped lang="scss">
.act-reject__input {
  width: 100%;
  padding: 0.375rem 0.625rem;
  border: 0.0625rem solid $zs-border-input;
  border-radius: 0.25rem;
  color: $zs-text;
  font: inherit;
  font-size: 0.875rem;
  resize: none;
}
</style>
