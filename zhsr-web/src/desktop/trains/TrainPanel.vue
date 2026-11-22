<template>
  <!-- тело не прокручивается: прокручивается только таблица вагонов внизу -->
  <SidePanel
    :empty="train ? '' : 'Выберите поезд в списке'"
    :title="train ? t('Поезд {name}', { name: train.name }) : ''"
    :subtitle="train ? t('{n} вагонов', train.wagons.length) : ''"
    :closable="closable"
    :scroll="false"
    @close="emit('close')"
  >
    <template v-if="train">
      <SidePanelFields>
        <span>{{ t('Сформирован') }}</span>
        <span>{{ formatDateTime(train.dbeg) }}</span>
        <template v-if="train.dend">
          <span>{{ t('Расформирован') }}</span>
          <span>{{ formatDateTime(train.dend) }}</span>
        </template>
        <span>{{ t('Где вагоны') }}</span>
        <span>{{ trainPlaceText(train) || '—' }}</span>
        <span>{{ t('По плану') }}</span>
        <span>{{ t('{n} прицепок и отцепок', { n: train.plannedCount }) }}</span>
      </SidePanelFields>
      <!-- «Редактировать» — кнопкой, как у рейса, и в списке, и на экране состава; без прав видна, но неактивна,
           причина в подсказке -->
      <div class="q-mt-md">
        <q-btn
          unelevated
          outline
          no-caps
          color="primary"
          class="zs-soft-border"
          :disable="!!editReason"
          :title="editReason || undefined"
          :label="t('Редактировать')"
          @click="emit('edit')"
        />
      </div>

      <!-- рейсы — тем же блоком, что у вагона; от атрибутов — чертой во всю ширину панели -->
      <section v-if="trips.length > 0" class="train-panel__trips q-pt-md q-px-lg">
        <TripLinks :trips="trips" :title="pastTrips ? 'Прошлые рейсы' : 'Текущий и будущие рейсы'" />
      </section>

      <!-- вагоны поезда по порядку — под чертой ниже всего, таблицей с колонками «Номер» и «Тип» списка вагонов;
           прокручивается только она. Справа от подписи — «Состав поезда», как «Редактировать» у маршрута. На экране
           состава поезда блока нет: состав — в середине экрана -->
      <section v-if="!formPage" class="train-panel__wagons q-pt-md q-px-lg q-pb-md">
        <div class="train-panel__head">
          <span class="train-panel__title">{{ t('Вагоны') }}</span>
          <SoftLink text="Состав поезда" @click="emit('open')" />
        </div>
        <!-- вагонов нет — без таблицы, одной надписью -->
        <DataTable v-if="train.wagons.length > 0" :columns="wagonColumns" :rows="train.wagons" row-key="id" />
        <div v-else class="train-panel__empty">{{ t('Вагонов нет') }}</div>
      </section>
    </template>
  </SidePanel>
</template>

<script setup lang="ts">
import { computed } from 'vue'

import type { TrainCard, TrainRow, TrainRowWagon } from '@/api/trains'
import type { TripRef } from '@/api/wagons'
import { t } from '@/boot/i18n'
import { formatDateTime } from '@/shared/format'
import SidePanel from '../frame/SidePanel.vue'
import SidePanelFields from '../frame/SidePanelFields.vue'
import DataTable from '@/shared/components/table/DataTable.vue'
import type { Column } from '@/shared/components/table/types'
import { TripStatus } from '@/shared/constants/systemDicts'
import SoftLink from '@/shared/components/form/SoftLink.vue'
import TripLinks from '../trips/TripLinks.vue'
import { wagonsColumns } from '../wagons/wagonsColumns'
import { trainPlaceText } from './trainPlace'

/**
 * Панель поезда (Ф-ПОЕ-00): обозначение, где стоят вагоны (как расположение в списке вагонов), число плановых
 * прицепок и отцепок; под атрибутами — кнопка «Редактировать» (обозначение поезда, событие edit; editReason — почему
 * недоступна); ниже — рейсы поезда ссылками на состав поезда с рейсом и вагоны списком по порядку. На списке поездов —
 * блок «Вагоны» с кнопкой «Состав поезда»; на экране состава поезда (formPage) блока «Вагоны» нет (состав — в середине
 * экрана). Справочники Station, Depot, Yard, LocationKind, WagonType, WagonModel, TripState готовит страница.
 */
const props = withDefaults(
  defineProps<{
    train: TrainRow | TrainCard | null
    formPage?: boolean
    closable?: boolean
    editReason?: string
    /** прошлые рейсы вместо текущего и будущих (режим «Прошлое» экрана поезда); null — текущий и будущие */
    pastTrips?: TripRef[] | null
  }>(),
  { formPage: false, closable: true, editReason: '', pastTrips: null },
)
const emit = defineEmits<{ close: []; open: []; edit: [] }>()

/** Рейсы поезда — у строки списка и у карточки одинаково: выполняющийся и все в статусе «план». */
const trips = computed<{ trip: TripRef; tripStatusType: number }[]>(() => {
  const tr = props.train
  if (!tr) return []
  if (props.pastTrips) return props.pastTrips.map((x) => ({ trip: x, tripStatusType: TripStatus.DONE }))
  // выполняющийся — trip; остальные — в плане
  const status = (x: TripRef) => (tr.trip?.id === x.id ? TripStatus.IN_PROGRESS : TripStatus.PLANNED)
  return tr.trips.map((x) => ({ trip: x, tripStatusType: status(x) }))
})

// колонки «Номер» и «Тип» — те же, что в списке вагонов; строка вагона поезда несёт их поля
const wagonColumns = (wagonsColumns as unknown as Column<TrainRowWagon>[])
  .filter((c) => c.field === 'number' || c.field === 'wagonType')
  .map((c) => ({ ...c, sortable: false }))
</script>

<style scoped lang="scss">
// рейсы под чертой во всю ширину панели, как вагоны ниже
.train-panel__trips {
  flex: none;
  margin: 0 -1.5rem;
  border-top: 0.0625rem solid $zs-border;
}

// вагоны занимают всё место панели под атрибутами, отделены чертой во всю ширину панели
.train-panel__wagons {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 0.6rem;
  min-height: 0;
  margin: 0 -1.5rem -1rem;
  border-top: 0.0625rem solid $zs-border;
}

.train-panel__empty {
  color: $zs-text-muted;
}

.train-panel__head {
  display: flex;
  flex: none;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
}

.train-panel__title {
  color: $zs-text-muted;
  font-size: 0.9rem;
  font-weight: 600;
}
</style>
