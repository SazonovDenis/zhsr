<template>
  <DesktopFrame v-model:floating="floating" :back="back" :right-open="true">
    <template #header>
      <h1 class="desktop-frame__title">{{ attach ? t('Прицепка вагонов в рейсе') : t('Отцепка вагонов в рейсе') }}</h1>
      <div class="desktop-frame__actions">
        <CbSegment :model-value="kind" single :values="kinds" @update:model-value="switchKind($event as string)" />
      </div>
    </template>

    <ErrorBox v-if="card.error.value" :error="card.error.value" :retry="card.crew.value ? null : card.load" />
    <LoadSpinner v-if="busy.active.value && !card.crew.value" />

    <div v-if="card.crew.value" class="trip-coupling q-pb-md">
      <!-- что и где: остановка, время, позиция, основания, ответственные -->
      <div class="trip-coupling__act">
        <FormField :label="attach ? 'Остановка прицепки' : 'Остановка отцепки'">
          <CbList
            single
            no-search
            placeholder="Выберите…"
            :model-value="card.stop.value"
            :values="card.stops.value"
            @update:model-value="card.setStop($event as number | null)"
          />
        </FormField>
        <FormField :label="attach ? 'Время прицепки' : 'Время отцепки'">
          <DatePicker v-model="card.time.value" with-time :date-text="attach ? 'Время прицепки' : 'Время отцепки'" />
        </FormField>
        <!-- прицепка: вагоны встают подряд с позиции, вагоны с неё сдвигаются назад -->
        <FormField v-if="attach" label="Позиция в поезде">
          <CbList v-model="card.seq.value" single no-search no-clear :values="card.seqChoices.value" />
        </FormField>
        <FormField label="Основания, примечания">
          <NoteInput v-model="card.comment.value" :rows="3" />
        </FormField>
        <!-- ответственные за вагоны: бригада рейса и сотрудники депо станции остановки -->
        <FormField label="Ответственные за вагоны">
          <ResponsiblePicker
            v-model="card.responsible.value"
            :station="card.station.value"
            :trip="Number(id)"
            :disabled="!card.station.value"
          />
        </FormField>
        <div class="row q-gutter-sm">
          <q-btn
            unelevated
            no-caps
            color="primary"
            :disable="!card.canSubmit.value"
            :label="attach ? t('Прицепить') : t('Отцепить')"
            @click="submit"
          />
          <q-btn
            v-if="attach"
            unelevated
            outline
            no-caps
            color="primary"
            class="zs-soft-border"
            :disable="!card.returnFrom.value"
            :title="card.returnFrom.value ? '' : t('Все отмеченные вагоны должны быть в одном депо или парке отстоя')"
            :label="t('Вернуть на станцию')"
            @click="returnToStation"
          />
        </div>
      </div>

      <!-- вагоны: отцепка — которые едут в поезде, по позиции; прицепка — станции остановки, её депо и парка отстоя -->
      <div class="trip-coupling__list">
        <div class="trip-coupling__caption">
          {{ attach ? t('Вагоны на станции, в депо и парке отстоя') : t('Вагоны в поезде') }}
          <span class="trip-coupling__muted">{{ t('Отмечено: {n}', { n: card.selected.value.length }) }}</span>
        </div>
        <div class="trip-coupling__table">
          <label v-for="w in card.wagons.value" :key="w.number" class="trip-coupling__row q-py-sm q-px-md">
            <q-checkbox v-model="card.selected.value" :val="w.number" dense />
            <span class="trip-coupling__muted">{{ w.seq ?? '' }}</span>
            <span class="zs-mono trip-coupling__number">{{ w.number }}</span>
            <span>{{ dictManager.getValue('WagonType', w.wagonType) }}</span>
            <span class="zs-sub">{{ card.whereText(w) }}</span>
            <DictBadge :id="w.wagonStatusType" dict="WagonState" />
            <!-- что будет мешать планам вагона, если прицепить -->
            <span v-for="(note, i) in card.notesOf(w)" :key="i" class="trip-coupling__warn">
              <PhraseView :phrase="note" />
            </span>
          </label>
          <div v-if="!busy.active.value && card.wagons.value.length === 0" class="trip-coupling__muted q-pa-md">
            {{ attach && !card.station.value ? t('Выберите остановку') : t('Вагонов нет') }}
          </div>
        </div>
        <!-- отмеченные вагоны: что будет мешать их планам -->
        <ErrorBox v-if="card.problemsError.value" :error="card.problemsError.value" />
        <WagonProblemList :rows="card.problemRows.value" />
      </div>
    </div>

    <template #right>
      <SidePanel
        :empty="card.crew.value ? '' : ' '"
        :title="card.crew.value ? t('Поезд № {train}', { train: card.crew.value.trip.trainNumber }) : ''"
        :subtitle="card.crew.value?.trip.name ?? ''"
        :closable="false"
      >
        <template v-if="card.crew.value">
          <SidePanelFields>
            <TripTimes
              :planned-dt="card.crew.value.trip.plannedDt"
              :departed-dt="card.crew.value.trip.departedDt"
              :arrived-dt="card.crew.value.trip.arrivedDt"
              :duration="card.crew.value.trip.duration"
            />
            <span>{{ t('Состояние') }}</span>
            <span><DictBadge :id="card.crew.value.trip.tripStatusType" dict="TripState" /></span>
          </SidePanelFields>
          <!-- планы рейса: отметить, когда прицепка или отцепка по плану состоялась, или отменить -->
          <div class="trip-coupling__divider q-mt-md" />
          <div class="trip-coupling__caption q-mb-sm">{{ t('Планы рейса') }}</div>
          <div v-if="plans.length === 0" class="trip-coupling__muted">{{ t('Ожидающих планов нет') }}</div>
          <div v-for="c in plans" :key="changeKey(c)" class="trip-coupling__plan q-mb-sm">
            <span class="zs-mono">{{ changeNumbers(c) }}</span>
            <ChangeLine
              :change="c"
              :facts="card.crew.value.changes"
              :disable="actions.running.value"
              @mark="actions.mark"
              @cancel="actions.cancel"
            />
          </div>
        </template>
      </SidePanel>
    </template>
  </DesktopFrame>
</template>

<script setup lang="ts">
import { Notify } from 'quasar'
import { computed, ref, shallowRef } from 'vue'
import { useRouter } from 'vue-router'
import { openTripTrain } from './openTripTrain'

import { fetchTrain } from '@/api/trains'
import { t } from '@/boot/i18n'
import { provideBusy } from '@/shared/busy'
import CbList from '@/shared/components/choice/CbList.vue'
import CbSegment from '@/shared/components/choice/CbSegment.vue'
import DictBadge from '@/shared/components/DictBadge.vue'
import ErrorBox from '@/shared/components/ErrorBox.vue'
import FormField from '@/shared/components/form/FormField.vue'
import NoteInput from '@/shared/components/form/NoteInput.vue'
import LoadSpinner from '@/shared/components/LoadSpinner.vue'
import DatePicker from '@/shared/components/period/DatePicker.vue'
import { dictManager } from '@/shared/dict/DictManager'
import { shownError } from '@/shared/errors'
import { changeKey, changeNumbers, isWaiting } from '@/shared/trains/trainJournal'
import PhraseView from '@/shared/trains/PhraseView.vue'
import WagonProblemList from '@/shared/trains/WagonProblemList.vue'
import { placeCode } from '@/shared/transferPlace'
import { useCoupling } from '@/shared/trips/useCoupling'
import ResponsiblePicker from '@/shared/wagons/ResponsiblePicker.vue'
import DesktopFrame from '../frame/DesktopFrame.vue'
import SidePanel from '../frame/SidePanel.vue'
import SidePanelFields from '../frame/SidePanelFields.vue'
import ChangeLine from '../trains/ChangeLine.vue'
import { useTrainActions } from '../trains/useTrainActions'
import TripTimes from './TripTimes.vue'

/**
 * Прицепка и отцепка в рейсе (Ф-АКТ-01): переключатель «Прицепка» / «Отцепка» в шапке; слева — остановка, время,
 * позиция прицепки, основания и примечания, ответственные; рядом — вагоны с галочками: при отцепке — которые едут в
 * поезде, по позиции, при прицепке — станции остановки, её депо и парка отстоя. Вагон в депо или парке отстоя
 * прицепить нельзя: «Вернуть на станцию» открывает акт приёма-передачи (Ф-АКТ-02). Справа — рейс и его ожидающие
 * планы с «Отметить выполнение» и «Отменить план».
 */
const props = defineProps<{ id: string; kind: string }>()

const router = useRouter()
// общее ожидание страницы: спиннер на месте содержимого — на всю цепочку загрузок
const busy = provideBusy()
const floating = ref(false)
const kind = ref(props.kind)
const attach = computed(() => kind.value === 'attach')
const card = shallowRef(useCoupling(Number(props.id), props.kind === 'attach', busy))

const kinds = [
  { id: 'attach', text: 'Прицепка' },
  { id: 'detach', text: 'Отцепка' },
]

// назад — к составу поезда рейса
const back = () => void openTripTrain(router, Number(props.id))

void card.value.load()

/** Сменить вид: адрес страницы — с новым видом, данные — заново. */
function switchKind(next: string) {
  if (next === kind.value) return
  kind.value = next
  void router.replace({ name: 'trip-coupling', params: { id: props.id, kind: next } })
  card.value = useCoupling(Number(props.id), next === 'attach', busy)
  void card.value.load()
}

const plans = computed(() => (card.value.crew.value?.changes ?? []).filter(isWaiting))

const actions = useTrainActions({
  card: () => fetchTrain(card.value.crew.value!.trip.train!.id),
  at: () => card.value.crew.value!.trip.plannedDt,
  trip: () => Number(props.id),
  inProgress: () => true,
  changed: () => card.value.load(),
  failed: (e) => (card.value.error.value = shownError(e)),
})

function returnToStation() {
  void router.push({
    name: 'transfer',
    query: {
      from: placeCode({ station: null, depot: null, yard: null, ...card.value.returnFrom.value }),
      to: `s${card.value.station.value}`,
      wagons: card.value.selected.value.join(','),
    },
  })
}

async function submit() {
  if (!(await card.value.submit())) return
  Notify.create({
    message: attach.value ? t('Вагоны прицеплены') : t('Вагоны отцеплены'),
    type: 'positive',
    position: 'bottom',
  })
  back()
}
</script>

<style scoped lang="scss">
.trip-coupling {
  display: grid;
  grid-template-columns: 20rem minmax(0, 1fr);
  gap: 1.3rem;
  min-height: 0;
}

.trip-coupling__act {
  display: flex;
  flex-direction: column;
  gap: 0.8rem;
}

.trip-coupling__caption {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 1rem;
  color: $zs-text-muted;
  font-size: 0.9rem;
  font-weight: 600;
}

.trip-coupling__muted {
  color: $zs-text-muted;
  font-size: 0.8rem;
  font-weight: 400;
}

.trip-coupling__list {
  display: flex;
  flex-direction: column;
  gap: 0.8rem;
  min-width: 0;
  min-height: 0;
}

.trip-coupling__table {
  overflow-y: auto;
  border: 0.0625rem solid $zs-border;
  border-radius: 0.3rem;
  background: #fff;
}

.trip-coupling__row {
  display: grid;
  grid-template-columns: auto 1.6rem 5rem 9rem 1fr auto;
  align-items: center;
  gap: 0.6rem;
  border-top: 0.0625rem solid $zs-border-row;
  font-size: 0.8rem;
  cursor: pointer;

  &:first-child {
    border-top: 0;
  }
}

// предупреждения — отдельной строкой под вагоном, от колонки номера до конца
.trip-coupling__warn {
  grid-column: 3 / -1;
  color: $zs-text-muted;
  font-size: 0.7rem;
  font-style: italic;

  // фраза без номера вагона начинается с заглавной буквы
  &::first-letter {
    text-transform: uppercase;
  }
}

.trip-coupling__number {
  font-weight: 700;
}

.trip-coupling__plan {
  display: flex;
  flex-direction: column;
  gap: 0.1rem;
}

// черта во всю ширину панели
.trip-coupling__divider {
  margin: 0 -1.5rem 1rem;
  border-top: 0.0625rem solid $zs-border;
}
</style>
