<template>
  <FormDialog
    v-model:show="show"
    title="Новый маршрут"
    ok-text="Создать маршрут"
    hint="Остановки маршрута, время в пути и нормы часов вводятся в редакторе маршрута."
    :running="running"
    :error="error"
    @ok="save"
  >
    <FormSection columns="7rem minmax(0, 1fr)">
      <FormField label="Номер поезда">
        <TextInput v-model="draft.train" mono />
      </FormField>
      <FormField label="Сообщение">
        <TextInput v-model="draft.name" placeholder="Например, Астана — Караганда" />
      </FormField>
    </FormSection>

    <FormSection>
      <FormField label="Вид маршрута">
        <CbSegment v-model="draft.kind" class="route-new__kind" single required :values="dictChoices('RouteKind')" />
      </FormField>
    </FormSection>

    <FormSection columns="max-content">
      <FormField label="Дата ввода маршрута">
        <DatePicker
          v-model="draft.since"
          date-text="Дата ввода маршрута"
          :quick="['today', 'todayPlus1', 'nextMonthStart']"
        />
      </FormField>
    </FormSection>
  </FormDialog>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'

import type { Route, RouteDraft } from '@/api/routes'
import { createRoute } from '@/api/routes'
import { t } from '@/boot/i18n'
import CbSegment from '@/shared/components/choice/CbSegment.vue'
import FormDialog from '@/shared/components/dialog/FormDialog.vue'
import FormField from '@/shared/components/form/FormField.vue'
import DatePicker from '@/shared/components/period/DatePicker.vue'
import FormSection from '@/shared/components/form/FormSection.vue'
import TextInput from '@/shared/components/form/TextInput.vue'
import { dictChoices } from '@/shared/dict/dictChoices'
import { dictManager } from '@/shared/dict/DictManager'

/**
 * Диалог «Новый маршрут»: номер поезда, сообщение, вид маршрута и дата ввода. Создаёт маршрут без остановок;
 * результат — созданный маршрут, вызывающий открывает его в редакторе (см. openRouteNew).
 */

const show = defineModel<boolean>('show', { default: false })
const emit = defineEmits<{ done: [route: Route] }>()

const draft = reactive<RouteDraft>({
  train: '',
  name: '',
  kind: null,
  since: null,
})

const running = ref(false)
const error = ref('')

onMounted(() => dictManager.prepareDict('RouteKind'))

async function save() {
  if (!draft.train || !draft.name || !draft.kind || !draft.since) {
    error.value = t('Заполните все поля')
    return
  }
  error.value = ''
  running.value = true
  try {
    emit('done', await createRoute({ ...draft }))
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    running.value = false
  }
}
</script>

<style scoped lang="scss">
.route-new__kind {
  align-self: flex-start;
}
</style>
