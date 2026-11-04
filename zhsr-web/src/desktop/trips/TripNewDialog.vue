<template>
  <FormDialog
    v-model:show="show"
    :title="trip ? 'Редактирование рейса' : 'Создание рейса'"
    :ok-text="trip ? 'Сохранить' : 'Создать рейс'"
    width="40rem"
    hint="Рейс создаётся в статусе «План». Станция отправления — первая остановка маршрута, прибытия — последняя."
    :running="running"
    :error="error"
    @ok="save"
  >
    <FormSection>
      <FormField label="Маршрут">
        <RouteSelect v-model="draft.route" :routes="routes" />
        <SoftLink text="Создать новый маршрут" class="trip-new__new-route" @click="newRoute" />
      </FormField>
    </FormSection>

    <FormSection columns="max-content">
      <FormField label="Плановое отправление">
        <DatePicker
          v-model="draft.plannedDt"
          date-text="Плановое отправление"
          with-time
          :quick="['today', 'todayPlus1', 'todayPlus2', 'todayPlus3', 'inWeek']"
        />
      </FormField>
    </FormSection>
  </FormDialog>
</template>

<script setup lang="ts">
import { date } from 'quasar'
import { reactive, ref, shallowRef } from 'vue'
import { useRouter } from 'vue-router'

import type { RouteOption } from '@/api/routes'
import { fetchRouteOptions } from '@/api/routes'
import type { TripRow, TripDraft } from '@/api/trips'
import { createTrip, updateTrip } from '@/api/trips'
import { t } from '@/boot/i18n'
import type { ShownError } from '@/shared/errors'
import { formError, shownError } from '@/shared/errors'
import { provideBusy } from '@/shared/busy'
import FormDialog from '@/shared/components/dialog/FormDialog.vue'
import FormField from '@/shared/components/form/FormField.vue'
import DatePicker from '@/shared/components/period/DatePicker.vue'
import FormSection from '@/shared/components/form/FormSection.vue'
import SoftLink from '@/shared/components/form/SoftLink.vue'
import { openRouteNew } from '../routes/openRouteNew'
import RouteSelect from '../routes/RouteSelect.vue'

/**
 * Диалог «Создание рейса» и «Редактирование рейса» (задан trip): маршрут и плановое отправление. Открывается через
 * openDialog, результат — созданный или исправленный рейс. Нужного маршрута нет — «Создать новый маршрут» закрывает диалог и открывает «Новый маршрут».
 */

const props = withDefaults(defineProps<{ trip?: { id: number; route: number; plannedDt: string } | null }>(), {
  trip: null,
})

const show = defineModel<boolean>('show', { default: false })
// ожидание диалога: спиннер в подвале (FormDialog) — на всю цепочку загрузок формы
const busy = provideBusy()
const emit = defineEmits<{ done: [trip: TripRow] }>()

const router = useRouter()

const draft = reactive<TripDraft>({
  route: props.trip?.route ?? null,
  plannedDt: props.trip ? date.formatDate(props.trip.plannedDt, 'YYYY-MM-DDTHH:mm') : null,
})
const routes = shallowRef<RouteOption[]>([])

const running = ref(false)
// ошибка формы: проверки (formError) или запроса (shownError, запрос второй строкой); null — ошибки нет
const error = ref<ShownError | null>(null)

// в списке — маршруты всех видов, которые действуют сейчас; вид маршрута показан в строке маршрута
// ошибка загрузки маршрутов — в поле ошибки диалога
// загрузка формы — сразу в setup и в счёт ожидания диалога: спиннер в подвале с первой отрисовки
void busy.run(async () => {
  try {
    routes.value = await fetchRouteOptions({ kinds: [], states: ['active'] })
  } catch (e) {
    error.value = shownError(e)
  }
})

function newRoute() {
  show.value = false
  void openRouteNew(router)
}

async function save() {
  if (!draft.route || !draft.plannedDt) {
    error.value = formError(t('Заполните все поля'))
    return
  }
  error.value = null
  running.value = true
  try {
    emit('done', props.trip ? await updateTrip(props.trip.id, { ...draft }) : await createTrip({ ...draft }))
  } catch (e) {
    error.value = shownError(e)
  } finally {
    running.value = false
  }
}
</script>

<style scoped lang="scss">
// под выбором маршрута, у правого края — как в макете
.trip-new__new-route {
  align-self: flex-end;
}
</style>
