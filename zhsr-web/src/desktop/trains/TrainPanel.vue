<template>
  <SidePanel
    :empty="train ? '' : 'Выберите поезд в списке'"
    :title="train ? t('Поезд {name}', { name: train.name }) : ''"
    :subtitle="train ? t('{n} вагонов', train.wagons.length) : ''"
    :closable="closable"
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
        <span>{{ t('Вагоны') }}</span>
        <span class="zs-mono">{{ train.wagons.length ? train.wagons.join(', ') : t('нет вагонов') }}</span>
        <span>{{ t('Где вагоны') }}</span>
        <span>{{ where }}</span>
        <span>{{ t('По плану') }}</span>
        <span>{{ t('{n} прицепок и отцепок', { n: train.plannedCount }) }}</span>
      </SidePanelFields>

      <div v-if="!formPage" class="train-panel__actions q-mt-md">
        <q-btn unelevated no-caps color="primary" @click="emit('open')">{{ t('Состав поезда') }}</q-btn>
      </div>

      <div v-if="trips.length > 0" class="q-mt-md">
        <div class="train-panel__title">{{ t('Рейсы') }}</div>
        <div v-for="tr in trips" :key="tr.id" class="q-mt-xs">
          <button type="button" class="train-panel__link q-pa-none" @click="openTrip(tr.id)">
            {{ tripRefDatedText(tr) }}
          </button>
        </div>
      </div>
    </template>
  </SidePanel>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'

import type { TrainCard, TrainRow } from '@/api/trains'
import type { TripRef } from '@/api/wagons'
import { t } from '@/boot/i18n'
import { dictManager } from '@/shared/dict/DictManager'
import { formatDateTime } from '@/shared/format'
import { tripRefDatedText } from '@/shared/trips/tripRefText'
import SidePanel from '../frame/SidePanel.vue'
import SidePanelFields from '../frame/SidePanelFields.vue'

/**
 * Панель поезда (Ф-ПОЕ-00): обозначение, вагоны и где они стоят, число плановых прицепок и отцепок, рейсы поезда
 * ссылками на формирование рейса. На списке поездов — кнопка «Состав поезда»; на экране состава поезда (formPage) её
 * нет. Станции — справочник Station: его готовит страница.
 */
const props = withDefaults(
  defineProps<{ train: TrainRow | TrainCard | null; formPage?: boolean; closable?: boolean }>(),
  { formPage: false, closable: true },
)
const emit = defineEmits<{ close: []; open: [] }>()

const router = useRouter()

const where = computed(() => {
  const tr = props.train
  if (!tr) return ''
  if (tr.trip) return t('в рейсе')
  if (tr.station) return dictManager.getValue('Station', tr.station)
  return tr.wagons.length ? t('на разных станциях') : '—'
})

/** Рейсы поезда: у карточки — все в плане и выполняющиеся, у строки списка — выполняющийся и ближайший. */
const trips = computed<TripRef[]>(() => {
  const tr = props.train
  if (!tr) return []
  if ('trips' in tr) return tr.trips
  return [tr.trip, tr.nextTrip].filter((x): x is TripRef => !!x)
})

function openTrip(id: number) {
  void router.push({ name: 'trip-form', params: { id } })
}
</script>

<style scoped lang="scss">
.train-panel__actions {
  display: flex;
  justify-content: flex-end;
}

.train-panel__title {
  color: $zs-text-muted;
  font-size: 0.9rem;
  font-weight: 600;
}

.train-panel__link {
  border: 0;
  background: none;
  color: $primary;
  font: inherit;
  font-size: 0.8rem;
  font-weight: 600;
  text-align: left;
  cursor: pointer;
}
</style>
