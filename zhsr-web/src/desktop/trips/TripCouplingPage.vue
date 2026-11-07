<template>
  <DesktopFrame v-model:floating="floating" :back="back" :right-open="true">
    <template #header>
      <h1 class="desktop-frame__title">{{ attach ? t('Прицепка вагонов') : t('Отцепка вагонов') }}</h1>
    </template>

    <ErrorBox v-if="card.error.value" :error="card.error.value" :retry="card.crew.value ? null : card.load" />
    <LoadSpinner v-if="busy.active.value" />

    <div v-if="card.crew.value" class="trip-coupling">
      <div class="trip-coupling__act">
        <div class="trip-coupling__caption">{{ t('Акт') }}</div>
        <FormField :label="attach ? 'Станция прицепки' : 'Станция отцепки'">
          <CbList
            single
            no-search
            placeholder="Выберите…"
            :model-value="card.station.value"
            :values="card.stops.value"
            @update:model-value="card.setStation($event as number | null)"
          />
        </FormField>
        <FormField label="Время акта">
          <DatePicker v-model="card.time.value" with-time date-text="Время акта" />
        </FormField>
        <FormField label="Приказ">
          <input v-model="card.order.value" class="trip-coupling__input q-pa-sm" maxlength="100" />
        </FormField>
        <FormField label="Причина">
          <textarea v-model="card.reason.value" class="trip-coupling__input q-pa-sm" rows="4" />
        </FormField>
        <button type="button" class="zs-btn-primary" :disabled="!card.canSubmit.value" @click="submit">
          {{ attach ? t('Прицепить') : t('Отцепить') }}
        </button>
        <button
          v-if="attach"
          type="button"
          class="trip-coupling__btn q-py-none q-px-md"
          :disabled="!card.returnFrom.value"
          :title="card.returnFrom.value ? '' : t('Все отмеченные вагоны должны быть в одном депо или парке отстоя')"
          @click="returnToStation"
        >
          {{ t('Вернуть на станцию') }}
        </button>
      </div>

      <div class="trip-coupling__list">
        <div class="trip-coupling__caption">
          {{ attach ? t('Вагоны на станции, в депо и парке отстоя') : t('Вагоны в составе') }}
        </div>
        <div class="trip-coupling__table">
          <label v-for="w in card.wagons.value" :key="w.number" class="trip-coupling__row q-py-sm q-px-md">
            <q-checkbox
              v-model="card.selected.value"
              :val="w.number"
              :disable="!w.available && !card.returnable(w)"
              dense
            />
            <span class="zs-mono">{{ w.number }}</span>
            <span>{{ dictManager.getValue('WagonType', w.wagonType) }}</span>
            <span class="zs-sub">{{ card.whereText(w) }}</span>
            <DictBadge :id="w.wagonStatusType" dict="WagonState" />
          </label>
          <div v-if="!busy.active.value && card.wagons.value.length === 0" class="trip-coupling__empty q-pa-md">
            {{ attach && !card.station.value ? t('Выберите остановку') : t('Вагонов нет') }}
          </div>
        </div>
      </div>
    </div>

    <template #right>
      <SidePanel
        :empty="card.crew.value ? '' : ' '"
        :title="card.crew.value ? t('Поезд № {train}', { train: card.crew.value.trip.trainNumber }) : ''"
        :subtitle="card.crew.value?.trip.name ?? ''"
        :closable="false"
      >
        <SidePanelFields v-if="card.crew.value">
          <TripTimes
            :planned-dt="card.crew.value.trip.plannedDt"
            :departed-dt="card.crew.value.trip.departedDt"
            :arrived-dt="card.crew.value.trip.arrivedDt"
            :duration="card.crew.value.trip.duration"
          />
          <span>{{ t('Состояние') }}</span>
          <span><DictBadge :id="card.crew.value.trip.tripStatusType" dict="TripState" /></span>
        </SidePanelFields>
      </SidePanel>
    </template>
  </DesktopFrame>
</template>

<script setup lang="ts">
import { Notify } from 'quasar'
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'

import { t } from '@/boot/i18n'
import { provideBusy } from '@/shared/busy'
import CbList from '@/shared/components/choice/CbList.vue'
import DictBadge from '@/shared/components/DictBadge.vue'
import ErrorBox from '@/shared/components/ErrorBox.vue'
import FormField from '@/shared/components/form/FormField.vue'
import LoadSpinner from '@/shared/components/LoadSpinner.vue'
import DatePicker from '@/shared/components/period/DatePicker.vue'
import { TransferDirection } from '@/shared/constants/systemDicts'
import { dictManager } from '@/shared/dict/DictManager'
import { useCoupling } from '@/shared/trips/useCoupling'
import DesktopFrame from '../frame/DesktopFrame.vue'
import SidePanel from '../frame/SidePanel.vue'
import SidePanelFields from '../frame/SidePanelFields.vue'
import TripTimes from './TripTimes.vue'

/**
 * Экран акта отцепки или прицепки (Ф-АКТ-01): слева станция, приказ и причина, рядом вагоны с галочками, справа рейс.
 * Вагон в депо или парке отстоя прицепить нельзя: «Вернуть на станцию» открывает акт приёма-передачи (Ф-АКТ-02).
 */
const props = defineProps<{ id: string; kind: string }>()

const router = useRouter()
// общее ожидание страницы: спиннер на месте содержимого — на всю цепочку загрузок
const busy = provideBusy()
const floating = ref(false)
const attach = computed(() => props.kind === 'attach')
const card = useCoupling(Number(props.id), props.kind === 'attach', busy)

const back = () => void router.push({ name: 'trip-form', params: { id: props.id } })

void card.load()

function returnToStation() {
  void router.push({
    name: 'transfer',
    query: {
      dir: TransferDirection.FROM_DEPOT,
      station: card.station.value,
      ...card.returnFrom.value,
      wagons: card.selected.value.join(','),
    },
  })
}

async function submit() {
  if (!(await card.submit())) return
  Notify.create({
    message: attach.value ? t('Акт прицепки проведён') : t('Акт отцепки проведён'),
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
  color: $zs-text-muted;
  font-size: 0.8rem;
  font-weight: 600;
  letter-spacing: 0.1em;
  text-transform: uppercase;
}

.trip-coupling__input {
  width: 100%;
  border: 0.0625rem solid $zs-border-input;
  border-radius: 0.3rem;
  background: #fff;
  color: $zs-text;
  font: inherit;
  font-size: 0.9rem;
  resize: none;
}

.trip-coupling__btn {
  height: var(--zs-control-height);
  border: 0.0625rem solid $zs-border-input;
  border-radius: 0.3rem;
  background: #fff;
  color: $primary;
  font: inherit;
  font-size: 0.8rem;
  font-weight: 600;
  cursor: pointer;

  &:disabled {
    opacity: 0.45;
    cursor: default;
  }
}

.trip-coupling__list {
  display: flex;
  flex-direction: column;
  gap: 0.8rem;
  min-width: 0;
}

.trip-coupling__table {
  overflow-y: auto;
  border: 0.0625rem solid $zs-border;
  border-radius: 0.3rem;
  background: #fff;
}

.trip-coupling__row {
  display: grid;
  grid-template-columns: auto 5rem 9rem 1fr auto;
  align-items: center;
  gap: 0.6rem;
  border-top: 0.0625rem solid $zs-border-row;
  font-size: 0.8rem;
  cursor: pointer;

  &:first-child {
    border-top: 0;
  }
}

.trip-coupling__empty {
  color: $zs-text-muted;
  font-size: 0.8rem;
}
</style>
