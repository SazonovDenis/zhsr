<template>
  <FormDialog v-model:show="show" title="Приём вагонов" ok-text="Принять" :running="running" :error="error" @ok="save">
    <!-- ответственных назначил акт при оформлении; при приёме их можно сменить -->
    <FormField label="Ответственные за вагоны">
      <ResponsiblePicker
        v-model="responsible"
        :station="act.station"
        :wagons="act.wagons.map((w) => w.wagon)"
        :current="current"
      />
    </FormField>
  </FormDialog>
</template>

<script setup lang="ts">
import { ref } from 'vue'

import type { Transfer } from '@/api/transfers'
import { acceptTransfer } from '@/api/transfers'
import FormDialog from '@/shared/components/dialog/FormDialog.vue'
import FormField from '@/shared/components/form/FormField.vue'
import type { ShownError } from '@/shared/errors'
import { shownError } from '@/shared/errors'
import type { ResponsibleChoice } from '@/shared/wagons/responsible'
import { responsibleChoice, responsibleUsrs } from '@/shared/wagons/responsible'
import ResponsiblePicker from '@/shared/wagons/ResponsiblePicker.vue'

/**
 * Приём всех вагонов акта (Ф-АКТ-02): ответственные за вагоны — назначенные актом, их можно сменить на сотрудников
 * депо станции или «не назначен». Открывается через openDialog, результат — акт после приёма.
 */
const props = defineProps<{ act: Transfer }>()

const show = defineModel<boolean>('show', { default: false })
const emit = defineEmits<{ done: [act: Transfer] }>()

// ответственные, назначенные актом: одни на все вагоны акта
const current = props.act.wagons[0]?.usrs ?? []
const responsible = ref<ResponsibleChoice>(responsibleChoice(current))
const running = ref(false)
const error = ref<ShownError | null>(null)

async function save() {
  error.value = null
  running.value = true
  try {
    emit('done', await acceptTransfer(props.act.id, responsibleUsrs(responsible.value)))
  } catch (e) {
    error.value = shownError(e)
  } finally {
    running.value = false
  }
}
</script>
