<template>
  <FormDialog
    v-model:show="show"
    title="Создание рейса"
    ok-text="Создать рейс"
    width="40rem"
    hint="Рейс создаётся в статусе «План». Станция отправления — первая остановка маршрута, прибытия — последняя."
    :running="running"
    :error="error"
    @ok="save"
  >
    <FormSection>
      <FormField label="Маршрут">
        <RouteChoice v-model="draft.route" :routes="routes" />
        <SoftLink text="Создать новый маршрут" class="trip-new__new-route" @click="newRoute" />
      </FormField>
    </FormSection>

    <FormSection columns="max-content">
      <FormField label="Плановое отправление">
        <DatePicker
          v-model="draft.planned"
          date-text="Плановое отправление"
          with-time
          :quick="['today', 'todayPlus1', 'todayPlus2', 'todayPlus3', 'inWeek']"
        />
      </FormField>
    </FormSection>
  </FormDialog>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref, shallowRef } from 'vue'
import { useRouter } from 'vue-router'

import type { Route } from '@/api/routes'
import { fetchRoutes } from '@/api/routes'
import type { Trip, TripDraft } from '@/api/trips'
import { createTrip } from '@/api/trips'
import { t } from '@/boot/i18n'
import FormDialog from '@/shared/components/dialog/FormDialog.vue'
import FormField from '@/shared/components/form/FormField.vue'
import DatePicker from '@/shared/components/period/DatePicker.vue'
import FormSection from '@/shared/components/form/FormSection.vue'
import SoftLink from '@/shared/components/form/SoftLink.vue'
import { dictManager } from '@/shared/dict/DictManager'
import { openRouteNew } from '../routes/openRouteNew'
import RouteChoice from '../routes/RouteChoice.vue'

/**
 * Диалог «Создание рейса»: маршрут и плановое отправление. Открывается через openDialog, результат —
 * созданный рейс. Нужного маршрута нет — «Создать новый маршрут» закрывает диалог и открывает «Новый маршрут».
 */

const show = defineModel<boolean>('show', { default: false })
const emit = defineEmits<{ done: [trip: Trip] }>()

const router = useRouter()

const draft = reactive<TripDraft>({ route: null, planned: null })
const routes = shallowRef<Route[]>([])

const running = ref(false)
const error = ref('')

// в списке — маршруты всех видов, которые действуют сейчас; вид маршрута показан в строке маршрута
onMounted(async () => {
  await dictManager.prepareDict('RouteKind')
  routes.value = await fetchRoutes({ kinds: [], states: ['active'] }, null)
})

function newRoute() {
  show.value = false
  void openRouteNew(router)
}

async function save() {
  if (!draft.route || !draft.planned) {
    error.value = t('Заполните все поля')
    return
  }
  error.value = ''
  running.value = true
  try {
    emit('done', await createTrip({ ...draft }))
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
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
