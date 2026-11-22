<template>
  <div class="wagon-modes">
    <CbSegment
      class="wagon-modes__fixed"
      :model-value="pick.mode.value"
      single
      required
      :values="pick.modes.value"
      @update:model-value="setMode($event as WagonMode)"
    />

    <!-- мой вагон: карточки вагонов проводника; вагонов несколько — выбирает пользователь -->
    <template v-if="pick.mode.value === 'my'">
      <button
        v-for="w in pick.myWagons.value"
        :key="w.id"
        type="button"
        class="wagon-modes__mine wagon-modes__fixed q-pa-md"
        :class="{ 'wagon-modes__mine--on': selected?.id === w.id }"
        @click="emit('pick', w)"
      >
        <span class="wagon-modes__mine-text">
          <span class="wagon-modes__mine-number zs-mono">{{ w.number }}</span>
          <span class="wagon-modes__muted">{{ dictManager.getValue('WagonType', w.wagonType) }}</span>
        </span>
      </button>
      <LoadSpinner v-if="pick.loading.value && pick.myWagons.value.length === 0" size="1.5rem" />
      <ErrorBox v-else-if="pick.error.value" :error="pick.error.value" :retry="pick.load" />
    </template>

    <!-- мой рейс: состав открытым списком; другой вагон: поиск по цифрам среди всех; у вагона — расположение -->
    <template v-else>
      <TextInput
        v-if="pick.mode.value === 'other'"
        ref="numberInput"
        v-model="pick.query.value"
        class="wagon-modes__fixed"
        mono
        inputmode="numeric"
        placeholder="Введите цифры номера вагона"
      />
      <!-- «Другой вагон» до ввода цифр: списка нет совсем, кроме уже выбранного вагона -->
      <WagonList
        v-if="!(pick.mode.value === 'other' && !pick.query.value && !selected)"
        :items="listItems"
        :selected="selected ? [String(selected.id)] : []"
        @pick="onPick"
      >
        <div v-if="pick.limited.value" class="wagon-modes__empty q-py-md q-px-md">
          {{ t('Показаны первые {n} — уточните номер', { n: WAGON_SEARCH_LIMIT }) }}
        </div>
        <LoadSpinner v-if="pick.loading.value && pick.wagons.value.length === 0" size="1.5rem" />
        <ErrorBox v-else-if="pick.error.value" :error="pick.error.value" :retry="pick.load" />
        <div
          v-else-if="
            !pick.loading.value && pick.wagons.value.length === 0 && !(pick.mode.value === 'other' && !pick.query.value)
          "
          class="wagon-modes__empty q-py-lg q-px-md"
        >
          {{ t('Вагоны не найдены') }}
        </div>
      </WagonList>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, useTemplateRef, watch } from 'vue'

import type { CurrentUser } from '@/api/session'
import type { RequestWagon } from '@/api/wagons'
import { WAGON_SEARCH_LIMIT } from '@/api/wagons'
import { t } from '@/boot/i18n'
import CbSegment from '@/shared/components/choice/CbSegment.vue'
import ErrorBox from '@/shared/components/ErrorBox.vue'
import TextInput from '@/shared/components/form/TextInput.vue'
import LoadSpinner from '@/shared/components/LoadSpinner.vue'
import { dictManager } from '@/shared/dict/DictManager'
import { wagonBadge } from '@/shared/requests/requestView'
import { tripRefText } from '@/shared/trips/tripRefText'
import { placeText } from '@/shared/wagonPlace'
import type { WagonMode } from '../requests/useWizardWagon'
import { useWizardWagon } from '../requests/useWizardWagon'
import type { WagonListItem } from './WagonList.vue'
import WagonList from './WagonList.vue'

/**
 * Выбор вагона на телефоне — общий для подачи заявки, чек-листа и описи: три режима (useWizardWagon).
 *
 * - «Мой вагон» — карточки вагонов проводника; вагон один — выбирается сам.
 * - «Мой рейс» — состав текущего рейса открытым списком.
 * - «Другой вагон» — поиск по цифрам номера среди всех вагонов; новый поиск снимает выбор.
 *
 * На входе — данные пользователя (user: рейс, его вагоны, должность в бригаде) и выбранный вагон (selected); выбор
 * отдаётся событием pick: вагон или null — выбор снят. Смена режима снимает выбор. Режим по умолчанию — первый
 * доступный; уже выбранный вагон (исправление, возврат на шаг) — режим по нему: свой вагон, вагон своего рейса или
 * другой. Корень — колонка во всю высоту: прокручивается только список вагонов (страница — PwaPage с fill).
 */
const props = defineProps<{
  user: CurrentUser | null
  selected: { id: number; number: string; wagonType: number } | null
}>()

const emit = defineEmits<{ pick: [wagon: RequestWagon | null] }>()

const pick = useWizardWagon(() => props.user)
const numberInput = useTemplateRef<InstanceType<typeof TextInput>>('numberInput')

// режим выбрал пользователь или он задан по выбранному вагону: данные пользователя, пришедшие позже, его не меняют
let modeFixed = false

/** Плашка вагона: «Ваш вагон»; в «Другом вагоне» — и «Ваш рейс». */
function badgeOf(w: RequestWagon): string {
  return wagonBadge(props.user, w, pick.mode.value === 'other')
}

/** Где вагон сейчас. */
function placeOf(w: RequestWagon): string {
  return placeText(w.locationType, w.place, w.trip ? tripRefText(w.trip) : null)
}

/** Строки списка: выбранный вагон, которого нет в списке (вагон исправляемой записи), — первым. */
const listItems = computed<WagonListItem[]>(() => {
  const sel = props.selected
  const rows: WagonListItem[] = pick.wagons.value.map((w) => ({
    key: String(w.id),
    number: w.number,
    type: dictManager.getValue('WagonType', w.wagonType),
    place: placeOf(w),
    badge: badgeOf(w),
  }))
  if (sel && !pick.wagons.value.some((w) => w.id === sel.id)) {
    rows.unshift({ key: String(sel.id), number: sel.number, type: dictManager.getValue('WagonType', sel.wagonType) })
  }
  return rows
})

/** Выбор строки списка: вагон из списка; выбранный вне списка — без изменений. */
function onPick(key: string) {
  const w = pick.wagons.value.find((x) => String(x.id) === key)
  if (w) emit('pick', w)
}

/** Сменить режим: выбор снимается; «Мой вагон» выбирается сам; «Другой вагон» — фокус в поле номера. */
function setMode(value: WagonMode) {
  if (value === pick.mode.value) return
  modeFixed = true
  emit('pick', null)
  pick.setMode(value)
  if (value === 'other') void nextTick(() => numberInput.value?.focus())
}

// «Мой вагон»: вагон проводника один — выбирается сам, как только состав рейса загружен
watch(
  () => (pick.mode.value === 'my' ? pick.myWagons.value : []),
  (list) => {
    if (list.length === 1 && !props.selected) emit('pick', list[0]!)
  },
)

// «Другой вагон»: новый поиск — выбор заново
watch(
  () => pick.query.value,
  () => {
    if (pick.mode.value === 'other' && props.selected) emit('pick', null)
  },
)

// пользователь мог загрузиться после компонента: режим по умолчанию — по его рейсу
watch(
  () => pick.trip.value,
  () => {
    if (modeFixed || props.selected || pick.mode.value === pick.defaultMode()) return
    pick.mode.value = pick.defaultMode()
    void pick.load()
  },
)

onMounted(async () => {
  const sel = props.selected
  if (sel) {
    // уже выбранный вагон — режим по нему
    modeFixed = true
    pick.mode.value = 'other'
    pick.mode.value = await pick.modeOf(sel.id)
  }
  void pick.load()
})
</script>

<style scoped lang="scss">
.wagon-modes {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 0.5rem;
  min-height: 0;
}

// переключатель, карточки и поле номера не сжимаются: прокручивается только список вагонов
.wagon-modes__fixed {
  flex: none;
}

.wagon-modes__mine {
  display: flex;
  align-items: center;
  gap: 0.9rem;
  border: 0.1rem solid $zs-border;
  border-radius: 0.4rem;
  background: #fff;
  color: $zs-text;
  font: inherit;
  text-align: left;
  cursor: pointer;

  &--on {
    border-color: $primary;
    background: $zs-row-selected-bg;
  }
}

.wagon-modes__mine-text {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 0.1rem;
}

.wagon-modes__mine-number {
  font-size: 1.3rem;
  font-weight: 700;
}

.wagon-modes__muted {
  color: $zs-text-muted;
  font-size: 0.9rem;
}

.wagon-modes__empty {
  color: $zs-text-muted;
  font-size: 0.9rem;
}
</style>
