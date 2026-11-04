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
        <TextInput v-model="draft.trainNumber" mono />
      </FormField>
      <FormField label="Сообщение">
        <TextInput v-model="draft.name" placeholder="Например, Астана — Караганда" />
      </FormField>
    </FormSection>

    <FormSection>
      <FormField label="Вид маршрута">
        <CbSegment
          v-model="draft.routeKind"
          class="route-new__kind"
          single
          required
          :values="dictChoices('RouteKind')"
        />
      </FormField>
    </FormSection>

    <FormSection columns="max-content">
      <FormField label="Дата ввода маршрута">
        <DatePicker
          v-model="draft.dbeg"
          date-text="Дата ввода маршрута"
          :quick="['today', 'todayPlus1', 'nextMonthStart']"
        />
      </FormField>
    </FormSection>
  </FormDialog>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'

import type { Route, RouteDraft } from '@/api/routes'
import { createRoute } from '@/api/routes'
import { t } from '@/boot/i18n'
import type { ShownError } from '@/shared/errors'
import { formError, shownError } from '@/shared/errors'
import CbSegment from '@/shared/components/choice/CbSegment.vue'
import { provideBusy } from '@/shared/busy'
import FormDialog from '@/shared/components/dialog/FormDialog.vue'
import FormField from '@/shared/components/form/FormField.vue'
import DatePicker from '@/shared/components/period/DatePicker.vue'
import FormSection from '@/shared/components/form/FormSection.vue'
import TextInput from '@/shared/components/form/TextInput.vue'
import { dictChoices } from '@/shared/dict/dictChoices'

/**
 * Диалог «Новый маршрут»: номер поезда, сообщение, вид маршрута и дата ввода. Создаёт маршрут без остановок;
 * результат — созданный маршрут, вызывающий открывает его в редакторе (см. openRouteNew).
 */

const show = defineModel<boolean>('show', { default: false })
// ожидание диалога: спиннер в подвале (FormDialog) — на всю цепочку загрузок формы
provideBusy()
const emit = defineEmits<{ done: [route: Route] }>()

const draft = reactive<RouteDraft>({
  trainNumber: '',
  name: '',
  routeKind: null,
  dbeg: null,
})

const running = ref(false)
// ошибка формы: проверки (formError) или запроса (shownError, запрос второй строкой); null — ошибки нет
const error = ref<ShownError | null>(null)

async function save() {
  if (!draft.trainNumber || !draft.name || !draft.routeKind || !draft.dbeg) {
    error.value = formError(t('Заполните все поля'))
    return
  }
  error.value = null
  running.value = true
  try {
    emit('done', await createRoute({ ...draft }))
  } catch (e) {
    error.value = shownError(e)
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
