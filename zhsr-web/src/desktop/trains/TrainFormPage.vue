<template>
  <DesktopFrame v-model:floating="floating" :back="back" :right-open="true">
    <template #header>
      <h1 class="desktop-frame__title">{{ card ? t('Поезд {name}', { name: card.name }) : t('Поезд') }}</h1>
      <div v-if="card" class="desktop-frame__actions">
        <q-btn
          unelevated
          outline
          no-caps
          color="primary"
          class="zs-soft-border"
          :disable="!!detachReason || actions.running.value"
          :title="detachReason || undefined"
          :label="t('Отцепить вагоны')"
          @click="actions.detach()"
        />
        <q-btn
          unelevated
          no-caps
          color="primary"
          :disable="!!attachReason || actions.running.value"
          :title="attachReason || undefined"
          :label="t('Прицепить вагоны')"
          @click="actions.attach"
        />
      </div>
    </template>

    <ErrorBox v-if="error" :error="error" :retry="card ? null : load" class="q-mb-md" />
    <LoadSpinner v-if="busy.active.value && !card" />

    <div v-if="card" class="train-form q-pb-md">
      <TrainJournalList
        v-model:side="side"
        v-model:past-from="pastFrom"
        :card="card"
        :selected="selected"
        :disable="actions.running.value"
        :only="tripChanges"
        :selected-mark="selectedMark?.key ?? null"
        :only-trip="tripMoment?.id ?? null"
        :now-selected="nowSelected"
        @select="onSelect"
        @select-mark="onSelectMark"
        @select-now="onSelectNow"
        @clear-trip="pickTrip(null)"
        @pick-trip="pickTrip"
        @mark="actions.mark"
        @cancel="actions.cancel"
        @undo="actions.undo"
        @edit="actions.edit"
      />
      <div class="train-form__moment">
        <!-- рейс поезда: состав на отправление рейса, журнал — записи рейса; не выбран — состав сейчас -->
        <div class="row items-center q-gutter-x-md">
          <span class="train-form__caption">{{ t('Рейс') }}</span>
          <CbList
            :model-value="tripId"
            single
            placeholder="Рейс не выбран"
            :values="tripChoices"
            class="train-form__trip"
            @update:model-value="pickTrip($event as number | null)"
          />
        </div>
        <TrainMomentView
          :card="card"
          :change-id="selected"
          :future="side === 'future' || !!tripMoment"
          :couple-reason="detachReason"
          :trip="nowSelected ? null : tripMoment"
          :mark="selectedMark"
          class="train-form__view"
          @detach="(w, fact, plan, at) => void actions.detach(w, fact, plan, at)"
        />
      </div>
    </div>

    <template #right>
      <TrainPanel
        :train="card"
        form-page
        :past-trips="past"
        :closable="false"
        :edit-reason="card && !card.canEdit ? t('Поезд меняет планировщик вагонов') : ''"
        @edit="rename"
      />
    </template>
  </DesktopFrame>
</template>

<script setup lang="ts">
import { computed, ref, shallowRef, watch } from 'vue'
import { useRouter } from 'vue-router'

import type { TrainCard, TrainRow } from '@/api/trains'
import { fetchTrain } from '@/api/trains'
import type { TripCrew } from '@/api/trips'
import { fetchTripCrew } from '@/api/trips'
import { t } from '@/boot/i18n'
import { provideBusy } from '@/shared/busy'
import CbList from '@/shared/components/choice/CbList.vue'
import { openDialog } from '@/shared/components/dialog/openDialog'
import ErrorBox from '@/shared/components/ErrorBox.vue'
import LoadSpinner from '@/shared/components/LoadSpinner.vue'
import { dictManager } from '@/shared/dict/DictManager'
import type { ShownError } from '@/shared/errors'
import { shownError } from '@/shared/errors'
import { isoDateTime } from '@/shared/format'
import type { JournalMark } from '@/shared/trains/trainJournal'
import { tripRefDatedText } from '@/shared/trips/tripRefText'
import { pastTrips } from './pastTrips'
import DesktopFrame from '../frame/DesktopFrame.vue'
import type { JournalSide } from './journalSide'
import TrainJournalList from './TrainJournalList.vue'
import TrainMomentView from './TrainMomentView.vue'
import TrainNameDialog from './TrainNameDialog.vue'
import TrainPanel from './TrainPanel.vue'
import type { TripMoment } from './tripMoment'
import { useTrainActions } from './useTrainActions'

/**
 * Состав поезда (Ф-ПОЕ-02) по технике «журнал и состояние»: в середине — состав на момент (по умолчанию — сейчас, по
 * факту); слева — журнал прицепок и отцепок (виден всегда): прошлое —
 * факты, настоящее — текущее состояние и ожидающие планы. Выбор записи журнала показывает состав на её время: у факта — по факту, у плана —
 * ожидаемый. Прицепка и отцепка нескольких вагонов — кнопками в шапке, отцепка вагона — меню вагона, отметка и отмена
 * плана, удаление ошибочного факта — меню записи журнала. Справа — панель поезда с кнопкой «Редактировать»
 * (обозначение). Над составом — выбор рейса поезда (Ф-РЕЙ-02, адрес ?trip=): состав на отправление рейса (до
 * отправления — ожидаемый, у вагона не на станции отправления — «Оформить передачу»), журнал — записи рейса и записи
 * вне рейса за период от конца предыдущего рейса до начала следующего, план по умолчанию — к отправлению рейса. Проверки — на сервере; здесь только
 * доступность кнопок.
 */
const props = withDefaults(defineProps<{ id: string; trip?: string }>(), { trip: '' })

const router = useRouter()
const busy = provideBusy()
const trainId = computed(() => Number(props.id))
const floating = ref(false)

const card = shallowRef<TrainCard | null>(null)
const error = ref<ShownError | null>(null)
// выбранная запись журнала — момент состава; null — сейчас
const selected = ref<number | null>(null)
// выбранная отметка рейса в журнале — момент состава; выбор записи её снимает, и наоборот
const selectedMark = shallowRef<JournalMark | null>(null)

// выбран рейс и строка «Текущее состояние»: справа — текущий состав
const nowSelected = ref(false)

function onSelect(id: number | null) {
  selected.value = id
  selectedMark.value = null
  nowSelected.value = false
}

function onSelectMark(mark: JournalMark) {
  selected.value = null
  selectedMark.value = mark
  nowSelected.value = false
}

function onSelectNow() {
  selected.value = null
  selectedMark.value = null
  nowSelected.value = true
}
// журнал по умолчанию — настоящее: текущее состояние и ожидающие планы
const side = ref<JournalSide>('future')
// начало периода прошлого журнала: прошлые рейсы в панели и в выборе рейса — за тот же период
const pastFrom = ref<number | null>(null)
/** Прошлые рейсы за период журнала; режим не «Прошлое» — null (панель показывает текущий и будущие). */
const past = computed(() => (side.value === 'past' ? pastTrips(card.value, pastFrom.value) : null))

const back = () => void router.push({ name: 'trains' })

// --- рейс поезда ---

const tripId = computed(() => (props.trip ? Number(props.trip) : null))
const tripCrew = shallowRef<TripCrew | null>(null)

/** Выбрать рейс: адрес страницы — с рейсом, выбор записи журнала снимается. */
function pickTrip(id: number | null) {
  selected.value = null
  selectedMark.value = null
  nowSelected.value = false
  void router.replace({ name: 'train-form', params: { id: props.id }, query: id === null ? {} : { trip: String(id) } })
}

async function loadTrip() {
  const id = tripId.value
  if (id === null) {
    tripCrew.value = null
    return
  }
  try {
    const crew = await fetchTripCrew(id)
    if (tripId.value === id) tripCrew.value = crew
  } catch (e) {
    error.value = shownError(e)
  }
}
watch(tripId, () => {
  selected.value = null
  selectedMark.value = null
  nowSelected.value = false
  void loadTrip()
})

/** Выбранный рейс для состава; рейс ещё грузится — null. */
const tripMoment = computed<TripMoment | null>(() => {
  const c = tripCrew.value
  if (!c || c.trip.id !== tripId.value) return null
  return {
    id: c.trip.id,
    text: tripRefDatedText(c.trip),
    number: c.trip.trainNumber,
    at: c.trip.departedDt ?? c.trip.plannedDt,
    fact: c.trip.departedDt !== null,
    departureStation: c.trip.departureStation,
  }
})

/**
 * Записи журнала рейса: все записи рейса (факты и действующие планы) и записи вне рейса за период рейса — от конца
 * предыдущего рейса поезда (фактическое прибытие, иначе плановое) до начала следующего (фактическое отправление, иначе
 * плановое); нет предыдущего или следующего — без границы с этой стороны. Факт, который отработал план, попадает в
 * период по времени плана. Рейс не выбран — null.
 */
const tripChanges = computed<Set<number> | null>(() => {
  const c = card.value
  const id = tripMoment.value?.id
  if (!c || id === undefined) return null
  const trips = [...c.tripMarks].sort((a, b) => new Date(a.plannedDt).getTime() - new Date(b.plannedDt).getTime())
  const i = trips.findIndex((m) => m.trip.id === id)
  const prev = i > 0 ? trips[i - 1]! : null
  const next = i >= 0 && i < trips.length - 1 ? trips[i + 1]! : null
  const from = prev ? new Date(prev.arrivedDt ?? prev.arrival).getTime() : -Infinity
  const to = next ? new Date(next.departedDt ?? next.plannedDt).getTime() : Infinity
  return new Set(
    c.changes
      .filter((x) => {
        if (x.trip) return x.trip.id === id
        // факт, который отработал план, — по времени плана: отмеченный раньше срока план не пропадает из рейса
        const at = new Date(x.planDt ?? x.dt).getTime()
        return i >= 0 && at >= from && at < to
      })
      .map((x) => x.id),
  )
})

/** Варианты рейса: рейсы поезда в статусе «план» и «выполняется»; выбранный другой рейс — тоже. */
/** Рейсы для выбора: в режиме «Прошлое» — прошлые (выполненные, сначала последний), иначе текущий и будущие. */
const tripChoices = computed(() => {
  const list = past.value ? [...past.value] : [...(card.value?.trips ?? [])]
  const c = tripCrew.value
  if (c && !list.some((x) => x.id === c.trip.id)) list.unshift(c.trip)
  return list.map((x) => ({ id: x.id, text: tripRefDatedText(x) }))
})

/** Почему не прицепить: нет прав; пусто — можно. Прицепка к расформированному поезду снова открывает его. */
const attachReason = computed(() => {
  const c = card.value
  if (!c || c.canCouple) return ''
  return c.dend
    ? t('Прицепляет вагоны к расформированному поезду планировщик вагонов')
    : t('Прицепляет и отцепляет планировщик вагонов, у поезда в рейсе — ещё ЛНП рейса и мастер депо')
})

/** Почему не отцепить: поезд расформирован (вагонов нет) или нет прав; пусто — можно. */
const detachReason = computed(() =>
  card.value?.dend ? t('Поезд расформирован: в нём нет вагонов') : attachReason.value,
)

async function load() {
  error.value = null
  try {
    await busy.run(async () => {
      const [data] = await Promise.all([
        fetchTrain(trainId.value),
        dictManager.prepareDicts('Station', 'Depot', 'Yard', 'WagonType', 'WagonModel', 'LocationKind', 'TripState'),
      ])
      card.value = data
    })
    await loadTrip()
  } catch (e) {
    error.value = shownError(e)
  }
}
void load()

/** Состав изменился: ответ сервера — поезд после изменения; null — перечитать. Выбранной записи может не стать. */
async function changed(next: TrainCard | null) {
  error.value = null
  if (next) {
    card.value = next
    await loadTrip()
  } else await load()
  if (selected.value !== null && !card.value?.changes.some((c) => c.id === selected.value)) selected.value = null
}

const actions = useTrainActions({
  card: () => Promise.resolve(card.value!),
  // выбран рейс до отправления — план по умолчанию к отправлению рейса
  at: () => (tripMoment.value && !tripMoment.value.fact ? tripMoment.value.at : isoDateTime(new Date())),
  trip: () => tripId.value,
  // card.trip — рейс поезда в статусе «выполняется»
  inProgress: (c) => !!c.trip && card.value?.trip?.id === c.trip.id,
  changed,
  failed: (e) => (error.value = shownError(e)),
})

async function rename() {
  const tr = card.value!
  const saved = await openDialog<TrainRow>(TrainNameDialog, { train: { id: tr.id, name: tr.name } })
  if (saved) await load()
}
</script>

<style scoped lang="scss">
.train-form {
  display: grid;
  flex: 1;
  grid-template-columns: 22.4rem minmax(0, 1fr);
  gap: 1rem;
  min-height: 0;
}

.train-form__moment {
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
  min-width: 0;
  min-height: 0;
}

.train-form__view {
  flex: 1;
}

// выбор рейса растягивается на свободную ширину строки, но не шире 40rem
.train-form__trip {
  flex: 1 1 20rem;
  min-width: 20rem;
  max-width: 40rem;
}

.train-form__caption {
  color: $zs-text-muted;
  font-size: 0.8rem;
  font-weight: 500;
}
</style>
