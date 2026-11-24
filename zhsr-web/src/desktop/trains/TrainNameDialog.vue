<template>
  <FormDialog
    v-model:show="show"
    min-height="sm"
    :title="train ? 'Обозначение поезда' : 'Новый поезд'"
    :ok-text="train ? 'Сохранить' : 'Создать поезд'"
    hint="Поезд создаётся без вагонов; вагоны прицепляются в составе поезда или в формировании рейса."
    :running="running"
    :error="error"
    @ok="save"
  >
    <FormField label="Обозначение">
      <TextInput v-model="name" placeholder="Например, Тулпар-1" />
    </FormField>
  </FormDialog>
</template>

<script setup lang="ts">
import { ref } from 'vue'

import type { TrainRow } from '@/api/trains'
import { createTrain, renameTrain } from '@/api/trains'
import { t } from '@/boot/i18n'
import FormDialog from '@/shared/components/dialog/FormDialog.vue'
import FormField from '@/shared/components/form/FormField.vue'
import TextInput from '@/shared/components/form/TextInput.vue'
import type { ShownError } from '@/shared/errors'
import { formError, shownError } from '@/shared/errors'

/**
 * Создание поезда и правка обозначения (Ф-ПОЕ-01): задан train — правка. Открывается через openDialog, результат —
 * поезд после сохранения.
 */
const props = withDefaults(defineProps<{ train?: { id: number; name: string } | null }>(), { train: null })

const show = defineModel<boolean>('show', { default: false })
const emit = defineEmits<{ done: [train: TrainRow] }>()

const name = ref(props.train?.name ?? '')
const running = ref(false)
const error = ref<ShownError | null>(null)

async function save() {
  if (!name.value.trim()) {
    error.value = formError(t('Укажите обозначение поезда'))
    return
  }
  error.value = null
  running.value = true
  try {
    emit('done', props.train ? await renameTrain(props.train.id, name.value) : await createTrain(name.value))
  } catch (e) {
    error.value = shownError(e)
  } finally {
    running.value = false
  }
}
</script>
