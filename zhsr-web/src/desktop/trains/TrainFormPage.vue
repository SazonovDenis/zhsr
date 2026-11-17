<template>
  <DesktopFrame v-model:floating="floating" :back="back" :right-open="true">
    <template #header>
      <h1 class="desktop-frame__title">
        {{ train ? t('Состав поезда {name}', { name: train.name }) : t('Состав поезда') }}
      </h1>
      <div v-if="train" class="desktop-frame__actions">
        <q-btn
          unelevated
          outline
          no-caps
          color="primary"
          class="zs-soft-border"
          :disable="!!editReason || running"
          :title="editReason || undefined"
          @click="rename"
        >
          {{ t('Обозначение') }}
        </q-btn>
        <q-btn
          unelevated
          no-caps
          color="primary"
          :disable="!!editReason || running"
          :title="editReason || undefined"
          @click="attach"
        >
          {{ t('Прицепить вагоны') }}
        </q-btn>
      </div>
    </template>

    <ErrorBox v-if="error" :error="error" :retry="train ? null : load" class="q-mb-md" />
    <LoadSpinner v-if="busy.active.value" />

    <div v-if="train" class="train-form q-pb-md">
      <div class="train-form__table">
        <div class="train-form__row train-form__row--head q-py-sm q-px-md">
          <span>{{ t('Поз.') }}</span>
          <span>{{ t('Вагон') }}</span>
          <span>{{ t('Где сейчас') }}</span>
          <span>{{ t('Прицепка и отцепка') }}</span>
          <span />
        </div>
        <div v-for="(w, i) in train.trainWagons" :key="w.id" class="train-form__row q-py-sm q-px-md">
          <span>{{ i + 1 }}</span>
          <span>
            <span class="zs-mono train-form__number">{{ w.number }}</span>
            <span class="train-form__muted train-form__block">{{
              dictManager.getValue('WagonType', w.wagonType)
            }}</span>
          </span>
          <span>{{ where(w) }}</span>
          <span class="train-form__changes">
            <ChangeNote v-if="w.attachPlan" :change="w.attachPlan" @mark="mark" @cancel="cancelPlan" />
            <ChangeNote v-else-if="w.attached" :change="w.attached" undoable @undo="undo" />
            <ChangeNote v-if="w.detachPlan" :change="w.detachPlan" @mark="mark" @cancel="cancelPlan" />
          </span>
          <span class="train-form__moves">
            <button
              type="button"
              class="train-form__move q-pa-none"
              :disabled="!!editReason || i === 0"
              :title="editReason || t('Выше')"
              @click="move(i, -1)"
            >
              ↑
            </button>
            <button
              type="button"
              class="train-form__move q-pa-none"
              :disabled="!!editReason || i === train.trainWagons.length - 1"
              :title="editReason || t('Ниже')"
              @click="move(i, 1)"
            >
              ↓
            </button>
            <q-btn
              unelevated
              outline
              no-caps
              color="primary"
              class="zs-soft-border"
              :disable="!!detachReason(w)"
              :title="detachReason(w) || undefined"
              @click="detach(w)"
            >
              {{ t('Отцепить') }}
            </q-btn>
          </span>
        </div>
        <div v-if="train.trainWagons.length === 0" class="train-form__empty q-pa-md">
          {{ t('В поезде нет вагонов') }}
        </div>
      </div>
      <div class="train-form__muted">
        {{
          t(
            'Фактом прицепляется вагон на станции вагонов поезда, другие — планом. Прицепка и отцепка вне рейса не меняют расположение вагонов. Поезд в рейсе меняется прицепкой и отцепкой в рейсе.',
          )
        }}
      </div>
    </div>

    <template #right>
      <TrainPanel :train="train" form-page :closable="false" />
    </template>
  </DesktopFrame>
</template>

<script setup lang="ts">
import { Notify } from 'quasar'
import { computed, ref, shallowRef } from 'vue'
import { useRouter } from 'vue-router'

import type { TrainCard, TrainChange, TrainRow, TrainWagon } from '@/api/trains'
import { attachWagons, cancelChangePlan, detachWagons, fetchTrain, orderTrain } from '@/api/trains'
import { t } from '@/boot/i18n'
import { provideBusy } from '@/shared/busy'
import { openDialog } from '@/shared/components/dialog/openDialog'
import ErrorBox from '@/shared/components/ErrorBox.vue'
import LoadSpinner from '@/shared/components/LoadSpinner.vue'
import { LocationType, TripStatus } from '@/shared/constants/systemDicts'
import { dictManager } from '@/shared/dict/DictManager'
import { fetchTripCrew } from '@/api/trips'
import type { TripRef } from '@/api/wagons'
import type { ShownError } from '@/shared/errors'
import { notifyError, shownError } from '@/shared/errors'
import { isoDateTime } from '@/shared/format'
import { tripRefText } from '@/shared/trips/tripRefText'
import { placeText } from '@/shared/wagonPlace'
import DesktopFrame from '../frame/DesktopFrame.vue'
import type { AttachChoice, AttachStop, ChangeChoice } from './attach'
import { changeBody, tripStops } from './attach'
import AttachWagonsDialog from './AttachWagonsDialog.vue'
import ChangeNote from './ChangeNote.vue'
import ChangeUndoDialog from './ChangeUndoDialog.vue'
import DetachDialog from './DetachDialog.vue'
import MarkChangeDialog from './MarkChangeDialog.vue'
import TrainNameDialog from './TrainNameDialog.vue'
import TrainPanel from './TrainPanel.vue'

/**
 * Состав поезда (Ф-ПОЕ-02): вагоны поезда сейчас и с плановой прицепкой по порядку, прицепка и отцепка фактом и
 * планом, отметка и отмена плана, отмена ошибочного факта, порядок вагонов. Справа — панель поезда с рейсами. Меняют
 * поезд планировщик вагонов и администратор; поезд в рейсе в статусе «выполняется» здесь не меняется. Проверки — на
 * сервере; здесь только доступность кнопок.
 */
const props = defineProps<{ id: string }>()

const router = useRouter()
const trainId = computed(() => Number(props.id))
const busy = provideBusy()
const floating = ref(false)

const train = shallowRef<TrainCard | null>(null)
const error = ref<ShownError | null>(null)
const running = ref(false)

/** Почему поезд не поменять: нет прав, поезд расформирован или в рейсе; пусто — можно. */
const editReason = computed(() => {
  const tr = train.value
  if (!tr) return ''
  if (!tr.canEdit) return tr.dend ? t('Поезд расформирован') : t('Поезд меняет планировщик вагонов')
  if (tr.trip) return t('Поезд в рейсе: прицепка и отцепка оформляются в рейсе')
  return ''
})

/** Почему вагон не отцепить: как editReason; вагон уже с плановой отцепкой — тоже нельзя. */
function detachReason(w: TrainWagon): string {
  return editReason.value || (w.detachPlan ? t('Отцепка уже запланирована') : '')
}

const back = () => void router.push({ name: 'trains' })

async function load() {
  error.value = null
  try {
    await busy.run(async () => {
      const [data] = await Promise.all([
        fetchTrain(trainId.value),
        dictManager.prepareDicts('Station', 'Depot', 'Yard', 'WagonType', 'WagonModel', 'LocationKind', 'TripState'),
      ])
      train.value = data
    })
  } catch (e) {
    error.value = shownError(e)
  }
}
void load()

async function apply(work: () => Promise<TrainCard>) {
  error.value = null
  running.value = true
  try {
    train.value = await work()
  } catch (e) {
    error.value = shownError(e)
  } finally {
    running.value = false
  }
}

function where(w: TrainWagon): string {
  if (w.locationType === LocationType.TRIP) return placeText(w.locationType, null, w.trip ? tripRefText(w.trip) : null)
  return placeText(w.locationType, w.place)
}

async function rename() {
  const tr = train.value!
  const saved = await openDialog<TrainRow>(TrainNameDialog, { train: { id: tr.id, name: tr.name } })
  if (saved) await load()
}

async function attach() {
  const tr = train.value!
  const choice = await openDialog<AttachChoice>(AttachWagonsDialog, {
    trainId: tr.id,
    station: tr.station,
    at: isoDateTime(new Date()),
  })
  if (!choice) return
  await apply(() => attachWagons(tr.id, changeBody(choice.numbers, choice, null)))
}

async function detach(w: TrainWagon) {
  // остановки рейсов поезда в плане: план отцепки можно поставить и на остановке рейса
  let tripStopGroups: { trip: TripRef; stops: AttachStop[] }[]
  try {
    const crews = await busy.run(Promise.all(train.value!.trips.map((tr) => fetchTripCrew(tr.id))))
    tripStopGroups = crews
      .filter((c) => c.trip.tripStatusType === TripStatus.PLANNED)
      .map((c) => ({ trip: train.value!.trips.find((tr) => tr.id === c.trip.id)!, stops: tripStops(c.trip) }))
  } catch (e) {
    notifyError(e)
    return
  }
  const choice = await openDialog<ChangeChoice>(DetachDialog, {
    number: w.number,
    factAllowed: !!w.dbeg,
    station: w.station,
    tripStops: tripStopGroups,
  })
  if (!choice) return
  await apply(() => detachWagons(train.value!.id, changeBody([w.number], choice, null)))
}

function move(i: number, step: number) {
  const list = train.value!.trainWagons.map((w) => w.number)
  const [wagon] = list.splice(i, 1)
  list.splice(i + step, 0, wagon!)
  void apply(() => orderTrain(trainId.value, list))
}

async function mark(change: TrainChange) {
  const done = await openDialog<TrainCard>(MarkChangeDialog, { change, inProgress: false })
  if (done) train.value = done
}

function cancelPlan(change: TrainChange) {
  void apply(() => cancelChangePlan(change.id))
}

async function undo(change: TrainChange) {
  const done = await openDialog<true>(ChangeUndoDialog, {
    changeId: change.id,
    attach: true,
    number: change.number,
  })
  if (!done) return
  Notify.create({ message: t('Прицепка отменена'), position: 'bottom' })
  await load()
}
</script>

<style scoped lang="scss">
.train-form {
  display: flex;
  flex-direction: column;
  gap: 0.8rem;
  overflow-y: auto;
}

.train-form__table {
  border: 0.0625rem solid $zs-border;
  border-radius: 0.3rem;
  background: #fff;
}

.train-form__row {
  display: grid;
  grid-template-columns: 2.5rem 8rem minmax(9rem, 1fr) minmax(16rem, 2fr) 13rem;
  align-items: center;
  gap: 0.8rem;
  border-top: 0.0625rem solid $zs-border-row;
  font-size: 0.8rem;

  &--head {
    border-top: 0;
    background: $zs-bg-header;
    color: $zs-text-muted;
    font-weight: 600;
  }
}

.train-form__number {
  font-weight: 700;
}

.train-form__block {
  display: block;
}

.train-form__muted {
  color: $zs-text-muted;
  font-size: 0.8rem;
}

.train-form__changes {
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
}

.train-form__moves {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 0.3rem;
}

.train-form__move {
  width: 1.8rem;
  height: 1.8rem;
  border: 0.0625rem solid $zs-border-input;
  border-radius: 0.3rem;
  background: #fff;
  color: $zs-text;
  cursor: pointer;

  &:disabled {
    opacity: 0.35;
    cursor: default;
  }
}

.train-form__empty {
  color: $zs-text-muted;
  font-size: 0.8rem;
}
</style>
