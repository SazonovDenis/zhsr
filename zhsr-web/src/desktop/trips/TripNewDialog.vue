<template>
  <FormDialog
    v-model:show="show"
    :title="trip ? 'Редактирование рейса' : 'Создание рейса'"
    :ok-text="trip ? 'Сохранить' : 'Создать рейс'"
    width="40rem"
    :min-height="trip ? 'auto' : 'md'"
    :hint="
      trip
        ? ''
        : 'Рейс создаётся в статусе «План». Станция отправления — первая остановка маршрута, прибытия — последняя. Рейс без поезда не отправляется.'
    "
    :running="running"
    :error="error"
    @ok="save"
  >
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
      <FormField label="Маршрут">
        <RouteSelect v-model="draft.route" :routes="routes" />
        <SoftLink text="Создать новый маршрут" class="trip-new__new-route" @click="newRoute" />
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
        >
          <!-- строка поезда — как строка вагона: обозначение и число вагонов, справа где вагоны и плашка занятости -->
          <template #option="{ item }">
            <TrainOptionView
              :name="(item.data?.name as string | undefined) ?? ''"
              :wagons="(item.data?.wagons as string | undefined) ?? ''"
              :place="(item.data?.place as string | undefined) ?? ''"
              :busy="(item.data?.busy as string | undefined) ?? ''"
            />
          </template>
        </CbList>
        <SoftLink text="Собрать новый поезд" class="trip-new__new-route" @click="newTrain" />
      </FormField>
    </FormSection>

    <!-- предупреждения о сохранении — полосой над кнопками: подпись и маркированный список записей, как в журнале -->
    <template v-if="warnings.length > 0" #warnings>
      <div v-for="w in warnings" :key="w.title" class="trip-new__warning">
        <div>{{ w.title }}</div>
        <ul class="q-my-none q-pl-lg">
          <li v-for="c in w.changes" :key="changeKey(c)">
            <ChangeTitle :change="c" />{{ ' • ' + formatDateTime(c.dt) }}
          </li>
        </ul>
      </div>
    </template>
  </FormDialog>
</template>

<script setup lang="ts">
import { date } from 'quasar'
import { computed, reactive, ref, shallowRef, watch } from 'vue'
import { useRouter } from 'vue-router'

import type { Route, RouteOption } from '@/api/routes'
import { fetchRouteOptions } from '@/api/routes'
import type { TrainOption, TrainRow } from '@/api/trains'
import { fetchTrainOptions } from '@/api/trains'
import type { TrainChange } from '@/api/trains'
import type { TripCrew, TripRow, TripDraft } from '@/api/trips'
import { createTrip, fetchTripCrew, updateTrip } from '@/api/trips'
import { t } from '@/boot/i18n'
import type { ShownError } from '@/shared/errors'
import { formError, shownError } from '@/shared/errors'
import { ActiveState } from '@/shared/constants/ActiveState'
import { provideBusy } from '@/shared/busy'
import CbList from '@/shared/components/choice/CbList.vue'
import { openDialog } from '@/shared/components/dialog/openDialog'
import type { ChoiceItem } from '@/shared/components/choice/choice'
import FormDialog from '@/shared/components/dialog/FormDialog.vue'
import { dictManager } from '@/shared/dict/DictManager'
import { formatDateTime, isoDateTime } from '@/shared/format'
import ChangeTitle from '@/shared/trains/ChangeTitle.vue'
import { changeKey, isWaiting } from '@/shared/trains/trainJournal'
import { useLoad } from '@/shared/loadable'
import FormField from '@/shared/components/form/FormField.vue'
import DatePicker from '@/shared/components/period/DatePicker.vue'
import FormSection from '@/shared/components/form/FormSection.vue'
import SoftLink from '@/shared/components/form/SoftLink.vue'
import RouteNewDialog from '../routes/RouteNewDialog.vue'
import RouteSelect from '../routes/RouteSelect.vue'
import TrainNameDialog from '../trains/TrainNameDialog.vue'
import type { TripDialogTrip } from './tripDialogReturn'
import { reopenTripDialog } from './tripDialogReturn'
import TrainOptionView from '../trains/TrainOptionView.vue'
import { trainPlaceText } from '../trains/trainPlace'

/**
 * Диалог «Создание рейса» и «Редактирование рейса» (задан trip): маршрут, плановое отправление и поезд. Открывается
 * через openDialog, результат — созданный или исправленный рейс. Нужного маршрута или поезда нет — «Создать новый маршрут» и
 * «Собрать новый поезд» создают его и возвращают в это окно с ним выбранным. При правке смена поезда или маршрута
 * отменяет ожидающие планы прицепок и отцепок рейса, смена поезда оставляет у прежнего поезда его планы вне рейса до
 * отправления: их списки — предупреждениями над кнопками (слот warnings FormDialog), записи — как в журнале (ChangeTitle). Поезда грузятся своим списком при смене маршрута и времени: поезд, занятый в пересекающемся рейсе, не выбирается.
 */

const props = withDefaults(
  defineProps<{
    trip?: TripDialogTrip
    /** введённое раньше: окно открыто снова после перехода к новому маршруту или поезду */
    draft?: TripDraft | null
  }>(),
  { trip: null, draft: null },
)

const show = defineModel<boolean>('show', { default: false })
// ожидание диалога: спиннер в подвале (FormDialog) — на всю цепочку загрузок формы
const busy = provideBusy()
const emit = defineEmits<{ done: [trip: TripRow] }>()

const router = useRouter()

const draft = reactive<TripDraft>(
  props.draft
    ? { ...props.draft }
    : {
        route: props.trip?.route ?? null,
        plannedDt: props.trip ? date.formatDate(props.trip.plannedDt, 'YYYY-MM-DDTHH:mm') : null,
        train: props.trip?.train ?? null,
      },
)
const routes = shallowRef<RouteOption[]>([])

const running = ref(false)
// ошибка формы: проверки (formError) или запроса (shownError, запрос второй строкой); null — ошибки нет
const error = ref<ShownError | null>(null)

// в списке — маршруты всех видов, которые действуют сейчас; вид маршрута показан в строке маршрута
// ошибка загрузки маршрутов — в поле ошибки диалога
// загрузка формы — сразу в setup и в счёт ожидания диалога: спиннер в подвале с первой отрисовки
void busy.run(async () => {
  try {
    const active = await fetchRouteOptions({ kinds: [], states: [ActiveState.ACTIVE] })
    // выбранный маршрут — исправляемого рейса или только что созданный — может не действовать сегодня: он тоже в
    // списке, иначе поле было бы пустым
    const own = draft.route
    if (own && !active.some((r) => r.id === own)) {
      const all = await fetchRouteOptions({ kinds: [], states: [] })
      const route = all.find((r) => r.id === own)
      if (route) active.unshift(route)
    }
    routes.value = active
  } catch (e) {
    error.value = shownError(e)
  }
})

// исправляемый рейс: его ожидающие планы и планы поезда до отправления — для предупреждений о смене поезда или маршрута
const crew = shallowRef<TripCrew | null>(null)
const trip = props.trip
if (trip?.train) {
  void busy.run(async () => {
    try {
      crew.value = await fetchTripCrew(trip.id)
    } catch (e) {
      error.value = shownError(e)
    }
  })
}

/** Предупреждение: подпись и записи журнала под ней. */
interface Warning {
  title: string
  changes: TrainChange[]
}

/**
 * Предупреждения о сохранении: смена поезда или маршрута отменяет ожидающие планы рейса; смена поезда оставляет у
 * прежнего поезда его планы вне рейса до отправления (они мешали отправлению рейса и будут мешать следующему рейсу
 * того поезда).
 */
const warnings = computed<Warning[]>(() => {
  const c = crew.value
  if (!trip?.train || !c) return []
  const trainChanged = draft.train !== trip.train
  const res: Warning[] = []
  const own = c.changes.filter(isWaiting)
  if ((trainChanged || draft.route !== trip.route) && own.length > 0) {
    res.push({ title: t('Будут отменены планы прежнего рейса {trip}:', { trip: c.trip.trainNumber }), changes: own })
  }
  const outside = c.pending.filter((x) => !x.trip)
  if (trainChanged && outside.length > 0) {
    res.push({
      title: t('Останутся у прежнего поезда {train}:', { train: c.trip.train?.name ?? '' }),
      changes: outside,
    })
  }
  return res
})

// новый маршрут и новый поезд: окно закрывается, открывается окно создания; после него окно рейса открывается снова
// с тем, что было введено, и уже выбранным новым маршрутом или поездом (useTripDialogReturn)
async function newRoute() {
  show.value = false
  const route = await openDialog<Route>(RouteNewDialog)
  reopenTripDialog(router, props.trip, route ? { ...draft, route: route.id } : draft)
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
    dictManager.prepareDicts('Station', 'Depot', 'Yard', 'LocationKind'),
  ])
  trains.value = list
})
watch(
  () => [draft.route, draft.plannedDt, routes.value],
  () => void trainsLoad.run(),
  { immediate: true },
)

/** Строка поезда (TrainOptionView): обозначение, число вагонов, где вагоны; занятый — с плашкой рейса и недоступен. */
const trainChoices = computed<ChoiceItem[]>(() =>
  trains.value.map((tr) => {
    const wagons = tr.wagons.length ? t('{n} вагонов', tr.wagons.length) : t('вагонов нет')
    const busy = tr.busyTrip ? t('Занят') : ''
    return {
      id: tr.id,
      text: `${tr.name} • ${wagons}`,
      disabled: !!tr.busyTrip,
      data: { name: tr.name, wagons, place: trainPlaceText(tr), busy },
    }
  }),
)

async function newTrain() {
  show.value = false
  const train = await openDialog<TrainRow>(TrainNameDialog)
  reopenTripDialog(router, props.trip, train ? { ...draft, train: train.id } : draft)
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

// второе предупреждение — с отступом от первого
.trip-new__warning + .trip-new__warning {
  margin-top: 0.4rem;
}
</style>
