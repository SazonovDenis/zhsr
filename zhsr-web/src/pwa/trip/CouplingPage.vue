<template>
  <PwaPage
    :back="goBack"
    :title="attach ? t('Прицепка вагонов') : t('Отцепка вагонов')"
    :subtitle="card.crew.value ? `${card.crew.value.trip.trainNumber} • ${card.crew.value.trip.name}` : ''"
    fill
  >
    <!-- прогресс шагов — у нижнего края шапки, как у мастера ответственных -->
    <template #bottom>
      <div class="coupling__bars q-px-sm">
        <span v-for="i in 3" :key="i" class="coupling__bar" :class="{ 'coupling__bar--on': i - 1 <= step }" />
      </div>
    </template>

    <ErrorBox v-if="card.error.value" :error="card.error.value" :retry="card.crew.value ? null : card.load" />
    <LoadSpinner v-if="busy.active.value && !card.crew.value" />

    <template v-if="card.crew.value">
      <div v-if="!isOnline" class="coupling__muted">{{ t('Для действия нужна связь') }}</div>

      <!-- шаг 1: станция и вагоны — список, как выбор вагона заявки, с мультивыбором -->
      <template v-if="step === 0">
        <div class="coupling__field coupling__fixed">
          <span class="coupling__label">{{ attach ? t('Остановка прицепки') : t('Остановка отцепки') }}</span>
          <CbList
            :model-value="card.stop.value"
            single
            placeholder="Выберите…"
            :values="card.stops.value"
            @update:model-value="card.setStop(typeof $event === 'number' ? $event : null)"
          />
        </div>
        <!-- вагоны — только после выбора станции -->
        <template v-if="card.stop.value">
          <div class="coupling__label coupling__fixed">
            {{ attach ? t('Вагоны на станции') : t('Вагоны в поезде') }}
            <span class="coupling__muted">{{ t('Отмечено: {n}', { n: card.selected.value.length }) }}</span>
          </div>
          <!-- прицепка: поиск по цифрам номера, как «Другой вагон» в выборе вагона заявки -->
          <TextInput
            v-if="attach"
            v-model="query"
            class="coupling__fixed"
            mono
            inputmode="numeric"
            placeholder="Введите цифры номера вагона"
          />
          <WagonList :items="wagonItems" :selected="card.selected.value" multiple @pick="toggle">
            <LoadSpinner v-if="busy.active.value && card.wagons.value.length === 0" size="1.5rem" />
            <div v-else-if="card.wagons.value.length === 0" class="coupling__empty q-py-lg q-px-md">
              {{ t('Вагонов нет') }}
            </div>
          </WagonList>
        </template>
      </template>

      <!-- шаг 2: позиция (прицепка), время, основания -->
      <template v-else-if="step === 1">
        <div v-if="attach" class="coupling__field">
          <span class="coupling__label">{{ t('Позиция в поезде') }}</span>
          <CbList v-model="card.seq.value" single no-clear :values="card.seqChoices.value" />
        </div>
        <label class="coupling__field">
          <span class="coupling__label">{{ attach ? t('Время прицепки') : t('Время отцепки') }}</span>
          <DatePicker v-model="card.time.value" with-time :date-text="attach ? 'Время прицепки' : 'Время отцепки'" />
        </label>
        <label class="coupling__field">
          <span class="coupling__label">{{ t('Основания, примечания') }}</span>
          <NoteInput v-model="card.comment.value" />
        </label>
      </template>

      <!-- шаг 3: ответственные за отмеченные вагоны — видом первого шага экрана «Ответственные за вагоны» -->
      <ResponsibleBoard
        v-else
        v-model="responsibles"
        :wagons="chosen"
        :station="card.station.value"
        :trip="Number(id)"
      />
    </template>

    <template #bar>
      <BottomBar :actions="navActions" @action="onAction" />
    </template>
  </PwaPage>
</template>

<script setup lang="ts">
import { Notify } from 'quasar'
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'

import type { RequestWagon } from '@/api/wagons'
import { t } from '@/boot/i18n'
import { provideBusy } from '@/shared/busy'
import CbList from '@/shared/components/choice/CbList.vue'
import ErrorBox from '@/shared/components/ErrorBox.vue'
import NoteInput from '@/shared/components/form/NoteInput.vue'
import LoadSpinner from '@/shared/components/LoadSpinner.vue'
import DatePicker from '@/shared/components/period/DatePicker.vue'
import { dictManager } from '@/shared/dict/DictManager'
import { isOnline } from '@/shared/network'
import { useCoupling } from '@/shared/trips/useCoupling'
import TextInput from '@/shared/components/form/TextInput.vue'
import { LocationType } from '@/shared/constants/systemDicts'
import { wagonBadge } from '@/shared/requests/requestView'
import { tripRefText } from '@/shared/trips/tripRefText'
import { currentUser } from '@/shared/user'
import { placeText } from '@/shared/wagonPlace'
import ResponsibleBoard from '../components/ResponsibleBoard.vue'
import type { BottomBarAction } from '../components/BottomBar.vue'
import BottomBar from '../components/BottomBar.vue'
import PwaPage from '../components/PwaPage.vue'
import type { WagonListItem } from '../components/WagonList.vue'
import WagonList from '../components/WagonList.vue'

/**
 * Прицепка или отцепка в рейсе на телефоне (Ф-АКТ-01) — мастер из трёх шагов, вид задан кнопкой на главной
 * («Прицепка» или «Отцепка»), переключателя вида нет: 1) остановка и вагоны — список после выбора остановки, как выбор
 * вагона заявки (WagonList), с мультивыбором заливкой; у прицепки — поиск по цифрам номера; 2) позиция прицепки, время,
 * основания и примечания; 3) ответственные за отмеченные вагоны — видом первого шага экрана «Ответственные за вагоны»
 * (ResponsibleBoard), у каждого вагона свои: вагоны с одинаковыми ответственными уходят одним запросом. Нужна связь.
 * Плановые прицепки и отцепки рейса подтверждают с главной.
 */
const props = defineProps<{ id: string; kind: string }>()

const router = useRouter()
// общее ожидание страницы: спиннер на месте содержимого — на всю цепочку загрузок
const busy = provideBusy()
const attach = computed(() => props.kind === 'attach')
const card = useCoupling(Number(props.id), props.kind === 'attach', busy)
const step = ref(0)

void card.load()

// ответственные по номеру вагона; пусто — не назначен
const responsibles = ref<Record<string, number[]>>({})
/** Отмеченные вагоны — по порядку списка. */
const chosen = computed(() => card.wagons.value.filter((w) => card.selected.value.includes(w.number)))

// прицепка: цифры номера — отбор списка вагонов станции
const query = ref('')

/**
 * Строки списка — как в выборе вагона заявки: номер, тип, где вагон сейчас, плашка «Ваш вагон» / «Ваш рейс»;
 * недоступный вагон — бледный, причина — в расположении.
 */
const wagonItems = computed<WagonListItem[]>(() =>
  card.wagons.value
    .filter((w) => !query.value.trim() || w.number.includes(query.value.trim()))
    .map((w) => ({
      key: w.number,
      number: w.number,
      type: dictManager.getValue('WagonType', w.wagonType),
      place: !w.available
        ? card.whereText(w)
        : w.locationType === LocationType.TRIP
          ? placeText(w.locationType, null, card.crew.value ? tripRefText(card.crew.value.trip) : null)
          : placeText(w.locationType, w.place),
      badge: wagonBadge(currentUser.value, { number: w.number, trip: null } as RequestWagon, false),
      disabled: !w.available,
    })),
)

function toggle(number: string) {
  const list = card.selected.value
  card.selected.value = list.includes(number) ? list.filter((n) => n !== number) : [...list, number]
}

const navActions = computed<BottomBarAction[]>(() => {
  if (step.value === 0) {
    return [
      { key: 'back', title: 'Отмена', align: 'left' },
      {
        key: 'next',
        title: 'Далее',
        kind: 'primary',
        disabled: !card.stop.value || card.selected.value.length === 0,
      },
    ]
  }
  if (step.value === 1) {
    return [
      { key: 'back', title: 'Назад', align: 'left' },
      { key: 'next', title: 'Далее', kind: 'primary', disabled: !card.time.value },
    ]
  }
  return [
    { key: 'back', title: 'Назад', align: 'left' },
    {
      key: 'next',
      title: attach.value ? 'Прицепить' : 'Отцепить',
      kind: 'primary',
      loading: card.running.value,
      disabled:
        !card.stop.value ||
        !card.time.value ||
        chosen.value.length === 0 ||
        !chosen.value.every((w) => w.available) ||
        card.running.value ||
        !isOnline.value,
    },
  ]
})

function goBack() {
  if (window.history.state?.back) router.back()
  else void router.push({ name: 'pwa-home' })
}

async function onAction(key: string) {
  if (key === 'back') {
    if (step.value === 0) goBack()
    else step.value--
    return
  }
  if (step.value < 2) {
    step.value++
    return
  }
  if (!(await card.submitEach((n) => responsibles.value[n] ?? []))) return
  Notify.create({ message: attach.value ? t('Вагоны прицеплены') : t('Вагоны отцеплены'), position: 'bottom' })
  goBack()
}
</script>

<style scoped lang="scss">
.coupling__bars {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 0.3rem;
}

.coupling__bar {
  height: 0.2rem;
  border-radius: 0.1rem;
  background: $zs-border;

  // пройденные шаги и текущий — цветом кнопки
  &--on {
    background: $primary;
  }
}

// станция и подпись не сжимаются: прокручивается только список вагонов
.coupling__fixed {
  flex: none;
}

.coupling__field {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.coupling__label {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 0.8rem;
  font-size: 0.9rem;
  font-weight: 600;
}

.coupling__muted {
  color: $zs-text-muted;
  font-size: 0.8rem;
  font-weight: 400;
}

.coupling__empty {
  color: $zs-text-muted;
  font-size: 0.9rem;
}
</style>
