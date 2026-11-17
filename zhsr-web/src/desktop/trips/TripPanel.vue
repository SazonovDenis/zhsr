<template>
  <SidePanel
    :empty="trip ? '' : 'Выберите рейс в списке'"
    :title="trip ? t('Поезд № {train}', { train: trip.trainNumber }) : ''"
    :subtitle="trip?.name ?? ''"
    @close="emit('close')"
  >
    <SidePanelFields v-if="trip">
      <span>{{ t('Вид маршрута') }}</span>
      <span>{{ dictManager.getValue('TransportType', trip.transportType) }}</span>
      <span>{{ t('Время в пути') }}</span>
      <span>{{ formatHm(trip.duration) }}</span>
      <TripTimes
        :planned-dt="trip.plannedDt"
        :departed-dt="trip.departedDt"
        :arrived-dt="trip.arrivedDt"
        :duration="trip.duration"
      />
      <span>{{ t('Состояние') }}</span>
      <span><DictBadge :id="trip.tripStatusType" dict="TripState" /></span>
      <span>{{ t('Поезд') }}</span>
      <span>{{ trip.train?.name ?? t('не назначен') }}</span>
      <!-- после прибытия: ответственный за вагоны поезда — как исполнитель заявки: строка и ссылка; ссылка видна всегда,
           нельзя — неактивна, причина во всплывающей подсказке -->
      <template v-if="trip.tripStatusType === TripStatus.DONE">
        <span>{{ t('Ответственный за вагоны') }}</span>
        <span>
          <UserName v-if="responsibleUser" :user="responsibleUser" />
          <template v-else>{{ responsibleText }}</template>
          <SoftLink
            :text="crewTrip?.responsibleAssigned ? 'Переназначить' : 'Назначить'"
            :disabled="!assign || !!assign.reason"
            :title="assign?.reason || undefined"
            class="q-ml-sm"
            @click="assign?.run()"
          />
        </span>
      </template>
    </SidePanelFields>
    <!-- действия: видны все; нет прав или не тот статус рейса — кнопка неактивна, причина во всплывающей подсказке -->
    <div v-if="trip" class="trip-panel__actions">
      <q-btn
        v-for="b in buttons"
        :key="b.label"
        unelevated
        outline
        no-caps
        color="primary"
        class="zs-soft-border"
        :disable="!!b.reason"
        :title="b.reason || undefined"
        @click="b.run()"
      >
        {{ t(b.label) }}
      </q-btn>
    </div>
  </SidePanel>
</template>

<script setup lang="ts">
import { computed, onMounted, shallowRef, watch } from 'vue'

import UserName from '@/shared/components/UserName.vue'
import type { TripCrew, TripRow } from '@/api/trips'
import { fetchTripCrew } from '@/api/trips'
import { t } from '@/boot/i18n'
import { formatHm } from '@/desktop/routes/duration'
import DictBadge from '@/shared/components/DictBadge.vue'
import { dictManager } from '@/shared/dict/DictManager'
import SoftLink from '@/shared/components/form/SoftLink.vue'
import { TripStatus } from '@/shared/constants/systemDicts'
import { notifyError } from '@/shared/errors'
import { loadCurrentUser } from '@/shared/user'
import SidePanel from '../frame/SidePanel.vue'
import SidePanelFields from '../frame/SidePanelFields.vue'
import { useTripActions } from './tripActions'
import TripTimes from './TripTimes.vue'

/**
 * Правая панель списка рейсов: карточка выбранного рейса и кнопки действий с рейсом (useTripActions) — те же, что в
 * меню строки списка. Событие changed — рейс изменился.
 */
const props = defineProps<{ trip: TripRow | null }>()

const emit = defineEmits<{ close: []; changed: [] }>()

const { actionsOf } = useTripActions(() => emit('changed'))

/** Кнопки панели: видны все; reason — почему недоступно сейчас. */
// «Ответственный за вагоны» — не кнопкой, а ссылкой в строке панели
const ASSIGN = 'Ответственный за вагоны'
const actions = computed(() => (props.trip ? actionsOf(props.trip) : []))
const buttons = computed(() => actions.value.filter((a) => a.label !== ASSIGN))
const assign = computed(() => actions.value.find((a) => a.label === ASSIGN))

// ответственный за вагоны прибывшего рейса — из формирования рейса; рейс сменился или обновился — загружается заново
const crewTrip = shallowRef<TripCrew['trip'] | null>(null)
watch(
  () => props.trip,
  async (trip) => {
    crewTrip.value = null
    if (trip?.tripStatusType !== TripStatus.DONE) return
    try {
      const crew = await fetchTripCrew(trip.id)
      if (props.trip?.id === trip.id) crewTrip.value = crew.trip
    } catch (e) {
      notifyError(e)
    }
  },
  { immediate: true },
)

/** Назначенный ответственный; null — не назначен или ещё не назначали (тогда — responsibleText). */
const responsibleUser = computed(() => (crewTrip.value?.responsibleAssigned ? crewTrip.value.responsibleUsr : null))

/** Ответственный словами, когда сотрудника нет: «не назначен» или до назначения «проводники вагонов»; не загружен — многоточие. */
const responsibleText = computed(() => {
  const ct = crewTrip.value
  if (!ct) return '…'
  return ct.responsibleAssigned ? t('не назначен') : t('проводники вагонов')
})

onMounted(() => void loadCurrentUser())
</script>

<style scoped lang="scss">
.trip-panel__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
}
</style>
