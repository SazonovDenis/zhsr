<template>
  <!-- две строки, поля строк друг под другом не выравниваются: 1 — вид и рейс; 2 — станция, позиция, время, основания -->
  <div class="change-where">
    <div class="change-where__row">
      <FormField :label="w.attach ? 'Прицепка' : 'Отцепка'">
        <CbSegment v-model="w.state.mode" single required :values="w.modes" />
      </FormField>
      <!-- рейсы поезда в статусе «план» — богатым видом, как фильтр «Рейс» списка вагонов; пусто — вне рейса -->
      <FormField v-if="w.state.mode === 'plan'" label="В рейсе">
        <CbList v-model="w.state.trip" single placeholder="вне рейса" :values="w.tripChoices" style="width: 36rem">
          <template #option="{ item }">
            <TripOptionView v-if="item.data" :trip="tripOptionOf(item)" class="trip-option--list" />
            <CbOption v-else :item="item" />
          </template>
        </CbList>
      </FormField>
    </div>

    <div class="change-where__row">
      <!-- факт в рейсе — остановка маршрута в пути; план в рейсе — остановка рейса; время стоянки в списке справа -->
      <FormField v-if="stopList" label="Станция">
        <CbList v-model="stopModel" single no-clear :values="stopList" style="width: 18rem">
          <template #option="{ item }"><StopOption :item="item" /></template>
        </CbList>
      </FormField>
      <!-- прицепка вне рейса без общей станции вагонов — станцию выбирают -->
      <FormField v-else-if="w.attach && (w.state.mode === 'plan' || w.fixedStation === null)" label="Станция">
        <CbList
          v-model="w.state.station"
          single
          placeholder="Станция"
          :values="dictChoices('Station')"
          :source="dictSource('Station')"
          style="width: 14rem"
        />
      </FormField>
      <!-- станция вагонов, которую не меняют, — неактивным полем -->
      <FormField v-else-if="w.fixedStation !== null" label="Станция">
        <CbList
          :model-value="w.fixedStation"
          single
          disabled
          no-clear
          :values="dictChoices('Station')"
          :source="dictSource('Station')"
          style="width: 14rem"
        />
      </FormField>
      <!-- позиция — по составу на момент прицепки: текущему по факту или ожидаемому по плану -->
      <FormField v-if="w.attach" label="Позиция в поезде">
        <CbList v-model="w.state.seq" single no-clear :values="w.seqChoices" style="width: 14rem" />
      </FormField>
      <FormField v-if="w.state.mode === 'fact'" :label="w.attach ? 'Время прицепки' : 'Время отцепки'">
        <DatePicker v-model="w.state.factTime" with-time :date-text="w.attach ? 'Время прицепки' : 'Время отцепки'" />
      </FormField>
      <FormField v-else :label="planLabel">
        <DatePicker v-model="w.state.planTime" with-time :date-text="planLabel" />
      </FormField>
      <FormField label="Основания, примечания">
        <NoteInput v-model="w.state.comment" :rows="2" class="change-where__note" />
      </FormField>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

import type { TrainCard } from '@/api/trains'
import type { ChoiceItem, ChoiceModel } from '@/shared/components/choice/choice'
import CbList from '@/shared/components/choice/CbList.vue'
import CbOption from '@/shared/components/choice/CbOption.vue'
import CbSegment from '@/shared/components/choice/CbSegment.vue'
import FormField from '@/shared/components/form/FormField.vue'
import NoteInput from '@/shared/components/form/NoteInput.vue'
import DatePicker from '@/shared/components/period/DatePicker.vue'
import { dictChoices, dictSource } from '@/shared/dict/dictChoices'
import { tripOptionOf } from '../trips/tripOptionItem'
import TripOptionView from '../trips/TripOptionView.vue'
import type { StopGroup } from './attach'
import type { ChangeInitial } from './changeWhere'
import { useChangeWhere } from './changeWhere'
import StopOption from './StopOption.vue'

/**
 * Поля «как, где и куда» окон прицепки и отцепки (useChangeWhere) — только наши контролы, двумя строками без
 * выравнивания между ними: в первой — вид (обязательный выбор «Планом» / «Фактом») и у плана рейс, во второй — станция,
 * у прицепки позиция, время («Плановое время прицепки/отцепки» у плана) и основания. У плана —
 * «В рейсе» (рейсы поезда в статусе «план» богатым видом, пусто — вне рейса), станция (в рейсе — из остановок рейса,
 * время стоянки в списке справа) и плановое время; у факта — станция (неизменяемая — неактивным полем) и время; у
 * прицепки — позиция в поезде; основания и примечания — полем той же ширины, что соседние. Под полями подписей нет:
 * проверки — в build(), окно показывает их ошибкой диалога. Окно читает выбор через ref: build() — выбор или текст
 * ошибки, targetStation и moment — станция и момент. initial — исправление плана: поля заполнены его значениями.
 * Справочник Station готовит окно.
 */
const props = withDefaults(
  defineProps<{
    card: TrainCard
    groups: StopGroup[]
    station: number | null
    factAllowed?: boolean
    attach?: boolean
    at: string
    trip?: number | null
    plan?: boolean
    initial?: ChangeInitial | null
  }>(),
  { factAllowed: true, attach: false, trip: null, plan: false, initial: null },
)

const w = useChangeWhere(props)
defineExpose(w)

const planLabel = computed(() => (w.attach ? 'Плановое время прицепки' : 'Плановое время отцепки'))

/** Список станций рейса: факт в рейсе — остановки в пути, план в рейсе — остановки рейса; иначе null. */
const stopList = computed<ChoiceItem[] | null>(() => {
  if (w.state.mode === 'fact') return w.live ? w.factStopChoices : null
  return w.state.trip !== null ? w.stopChoices : null
})
/** Выбранная станция рейса — в своём поле состояния по виду. */
const stopModel = computed<ChoiceModel>({
  get: () => (w.state.mode === 'fact' ? w.state.factStop : w.state.stop),
  set: (v) => {
    if (w.state.mode === 'fact') w.state.factStop = v as number | null
    else w.state.stop = v as number | null
  },
})
</script>

<style scoped lang="scss">
.change-where {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

// поля строки — подряд слева направо, с переносом; со второй строкой не выравниваются
.change-where__row {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  gap: 1rem 1.5rem;
}

.change-where__note {
  width: 24rem;
}
</style>
