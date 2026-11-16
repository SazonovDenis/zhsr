<template>
  <FormDialog
    v-model:show="show"
    :title="trip ? 'Редактирование рейса' : 'Создание рейса'"
    :ok-text="trip ? 'Сохранить' : 'Создать рейс'"
    width="40rem"
    hint="Рейс создаётся в статусе «План». Станция отправления — первая остановка маршрута, прибытия — последняя. Рейс без поезда не отправляется."
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

    <FormSection>
      <!-- поезд, занятый в пересекающемся рейсе, виден, но не выбирается -->
      <FormField label="Поезд">
        <CbList
          v-model="draft.train"
          single
          placeholder="Поезд не назначен"
          :values="trainChoices"
          :source="trainsLoad"
        />
        <SoftLink text="Собрать новый поезд" class="trip-new__new-route" @click="newTrain" />
      </FormField>
    </FormSection>
  </FormDialog>
</template>

<script setup lang="ts">
import { date } from 'quasar'
import { computed, reactive, ref, shallowRef, watch } from 'vue'
import { useRouter } from 'vue-router'

import type { RouteOption } from '@/api/routes'
import { fetchRouteOptions } from '@/api/routes'
import type { TrainOption, TrainRow } from '@/api/trains'
import { fetchTrainOptions } from '@/api/trains'
import type { TripRow, TripDraft } from '@/api/trips'
import { createTrip, updateTrip } from '@/api/trips'
import { t } from '@/boot/i18n'
import type { ShownError } from '@/shared/errors'
import { formError, shownError } from '@/shared/errors'
import { provideBusy } from '@/shared/busy'
import CbList from '@/shared/components/choice/CbList.vue'
import type { ChoiceItem } from '@/shared/components/choice/choice'
import FormDialog from '@/shared/components/dialog/FormDialog.vue'
import { openDialog } from '@/shared/components/dialog/openDialog'
import { dictManager } from '@/shared/dict/DictManager'
import { isoDateTime } from '@/shared/format'
import { useLoad } from '@/shared/loadable'
import { tripRefDatedText } from '@/shared/trips/tripRefText'
import FormField from '@/shared/components/form/FormField.vue'
import DatePicker from '@/shared/components/period/DatePicker.vue'
import FormSection from '@/shared/components/form/FormSection.vue'
import SoftLink from '@/shared/components/form/SoftLink.vue'
import { openRouteNew } from '../routes/openRouteNew'
import RouteSelect from '../routes/RouteSelect.vue'
import TrainNameDialog from '../trains/TrainNameDialog.vue'

/**
 * Диалог «Создание рейса» и «Редактирование рейса» (задан trip): маршрут, плановое отправление и поезд. Открывается
 * через openDialog, результат — созданный или исправленный рейс. Нужного маршрута нет — «Создать новый маршрут»
 * закрывает диалог и открывает «Новый маршрут». Нужного поезда нет — «Собрать новый поезд» создаёт поезд и выбирает
 * его. Поезда грузятся своим списком при смене маршрута и времени: поезд, занятый в пересекающемся рейсе, не выбирается.
 */

const props = withDefaults(
  defineProps<{ trip?: { id: number; route: number; plannedDt: string; train: number | null } | null }>(),
  { trip: null },
)

const show = defineModel<boolean>('show', { default: false })
// ожидание диалога: спиннер в подвале (FormDialog) — на всю цепочку загрузок формы
const busy = provideBusy()
const emit = defineEmits<{ done: [trip: TripRow] }>()

const router = useRouter()

const draft = reactive<TripDraft>({
  route: props.trip?.route ?? null,
  plannedDt: props.trip ? date.formatDate(props.trip.plannedDt, 'YYYY-MM-DDTHH:mm') : null,
  train: props.trip?.train ?? null,
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

// поезда для выбора: занятость считается на время рейса — плановое отправление плюс время в пути маршрута
const trains = shallowRef<TrainOption[]>([])
const trainsLoad = useLoad(async () => {
  const route = routes.value.find((r) => r.id === draft.route)
  const start = draft.plannedDt ? new Date(draft.plannedDt) : null
  const from = start ? isoDateTime(start) : null
  const to = start && route ? isoDateTime(new Date(start.getTime() + route.duration * 60000)) : null
  const [list] = await Promise.all([
    fetchTrainOptions(from, to, props.trip?.id ?? null),
    dictManager.prepareDict('Station'),
  ])
  trains.value = list
})
watch(
  () => [draft.route, draft.plannedDt, routes.value],
  () => void trainsLoad.run(),
  { immediate: true },
)

/** Строка поезда: обозначение, число вагонов и их станция; занятый — с рейсом и недоступен. */
const trainChoices = computed<ChoiceItem[]>(() =>
  trains.value.map((tr) => {
    const where = tr.station ? ` • ${dictManager.getValue('Station', tr.station)}` : ''
    const busy = tr.busyTrip ? ` • ${t('занят: рейс {trip}', { trip: tripRefDatedText(tr.busyTrip) })}` : ''
    return {
      id: tr.id,
      text: `${tr.name} • ${t('{n} вагонов', tr.wagons.length)}${where}${busy}`,
      disabled: !!tr.busyTrip,
    }
  }),
)

async function newTrain() {
  const created = await openDialog<TrainRow>(TrainNameDialog)
  if (!created) return
  await trainsLoad.run()
  draft.train = created.id
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
