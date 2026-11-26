<template>
  <PwaPage :back="goBack" :title="title" :subtitle="subtitle" :fill="step === 1">
    <!-- в пути — два шага: отметка и ответственные; прогресс — у нижнего края шапки -->
    <template v-if="twoSteps" #bottom>
      <div class="mark__bars q-px-sm">
        <span v-for="i in 2" :key="i" class="mark__bar" :class="{ 'mark__bar--on': i - 1 <= step }" />
      </div>
    </template>
    <ErrorBox v-if="loadError" :error="loadError" :retry="load" />
    <LoadSpinner v-if="busy.active.value && !change" />

    <template v-if="change && step === 0">
      <div v-if="!isOnline" class="mark__muted">{{ t('Для действия нужна связь') }}</div>

      <!-- из плана — текстом, без полей: остановка, вагон, позиция, время по плану -->
      <div class="mark__attrs">
        <span>{{ t(stopLabel) }}</span>
        <span>{{ dictManager.getValue('Station', change.station) }}</span>
        <span>{{ seq ? t('Новый порядок') : t('Вагон') }}</span>
        <span class="zs-mono mark__number">{{ changeNumbers(change) }}</span>
        <template v-if="attach && change.seq !== null">
          <span>{{ t('Позиция в поезде') }}</span>
          <span>{{ change.seq }}</span>
        </template>
        <span>{{ t('Время по плану') }}</span>
        <span>{{ formatDateTime(change.dt) }}</span>
      </div>

      <label class="mark__field">
        <span class="mark__label">{{ t(timeLabel) }}</span>
        <DatePicker v-model="time" with-time :date-text="timeLabel" />
      </label>
      <label class="mark__field">
        <span class="mark__label">{{ t('Основания, примечания') }}</span>
        <NoteInput v-model="comment" />
      </label>
      <ErrorBox v-if="problemsError" :error="problemsError" />
      <WagonProblemList :rows="problemRows" />
      <ErrorBox v-if="error" :error="error" />
    </template>

    <!-- шаг 2 (в пути: прицепка и отцепка меняют журналы вагона): ответственные — видом первого шага экрана
         «Ответственные за вагоны»: бригада рейса и сотрудники станции -->
    <template v-if="change?.wagon && step === 1">
      <ResponsibleBoard
        v-model="responsibles"
        :wagons="[{ wagon: change.wagon.id, number: change.wagon.number, wagonType: wagonType }]"
        :station="change.station"
        :trip="Number(id)"
      />
      <ErrorBox v-if="error" :error="error" />
    </template>

    <template #bar>
      <BottomBar :actions="navActions" @action="onAction" />
    </template>
  </PwaPage>
</template>

<script setup lang="ts">
import { date } from 'quasar'
import { computed, ref, shallowRef } from 'vue'
import { useRouter } from 'vue-router'

import type { TrainChange } from '@/api/trains'
import { markChange } from '@/api/trains'
import type { TripCrew } from '@/api/trips'
import { fetchTripCrew } from '@/api/trips'
import { t } from '@/boot/i18n'
import { provideBusy } from '@/shared/busy'
import ErrorBox from '@/shared/components/ErrorBox.vue'
import NoteInput from '@/shared/components/form/NoteInput.vue'
import LoadSpinner from '@/shared/components/LoadSpinner.vue'
import DatePicker from '@/shared/components/period/DatePicker.vue'
import { TripStatus } from '@/shared/constants/systemDicts'
import { dictManager } from '@/shared/dict/DictManager'
import type { ShownError } from '@/shared/errors'
import { formError, shownError } from '@/shared/errors'
import { formatDateTime, isoDateTime } from '@/shared/format'
import { isOnline } from '@/shared/network'
import { useWagonProblems } from '@/shared/trains/useWagonProblems'
import WagonProblemList from '@/shared/trains/WagonProblemList.vue'
import { changeNumbers, isAttach, isSeq } from '@/shared/trains/trainJournal'
import PwaPage from '../components/PwaPage.vue'
import ResponsibleBoard from '../components/ResponsibleBoard.vue'
import type { BottomBarAction } from '../components/BottomBar.vue'
import BottomBar from '../components/BottomBar.vue'

/**
 * Подтверждение плановой прицепки или отцепки рейса на телефоне (Ф-АКТ-01): как экран прицепки и отцепки, но без
 * выбора вида — остановка, вагон, позиция и время по плану показаны текстом, без полей; пользователь указывает
 * фактическое время, основания (по умолчанию — основания плана); в пути — вторым шагом ответственных за вагон (видом
 * первого шага экрана «Ответственные за вагоны»). Нужна связь.
 *
 * @prop id     рейс
 * @prop code   таблица записи плана (TrainJournalCode)
 * @prop change план прицепки, отцепки или смены порядка вагонов (id записи таблицы code); у смены порядка вместо
 *              вагона и позиции — новый порядок, шага ответственных нет
 */
const props = defineProps<{ id: string; code: string; change: string }>()

const router = useRouter()
const busy = provideBusy()

const crew = shallowRef<TripCrew | null>(null)
const change = computed<TrainChange | null>(
  () => crew.value?.changes.find((c) => c.code === props.code && c.id === Number(props.change)) ?? null,
)
const seq = computed(() => !!change.value && isSeq(change.value))
const attach = computed(() => !!change.value && isAttach(change.value))
/** Два шага: прицепка и отцепка в рейсе в пути — ещё ответственные; смена порядка журналы вагона не меняет. */
const twoSteps = computed(() => !seq.value && crew.value?.trip.tripStatusType === TripStatus.IN_PROGRESS)

const title = computed(() => t(confirmText.value))
const confirmText = computed(() =>
  seq.value ? 'Подтвердить смену порядка' : attach.value ? 'Подтвердить прицепку' : 'Подтвердить отцепку',
)
const stopLabel = computed(() =>
  seq.value ? 'Остановка смены порядка' : attach.value ? 'Остановка прицепки' : 'Остановка отцепки',
)
const timeLabel = computed(() =>
  seq.value ? 'Время смены порядка' : attach.value ? 'Время прицепки' : 'Время отцепки',
)
const subtitle = computed(() => (crew.value ? `${crew.value.trip.trainNumber} • ${crew.value.trip.name}` : ''))

const time = ref<string | null>(date.formatDate(new Date(), 'YYYY-MM-DDTHH:mm'))
const comment = ref('')
// что будет мешать планам вагона, если подтвердить
const { rows: problemRows, error: problemsError } = useWagonProblems(() => {
  const c = change.value
  if (!c?.wagon || !isOnline.value) return null
  return {
    train: c.train.id,
    wagons: [c.wagon.id],
    changeType: c.changeType!,
    fact: true,
    time: time.value ? isoDateTime(new Date(time.value)) : null,
    station: c.station,
    routeStation: c.routeStation,
    plan: c.id,
  }
})
// ответственные за вагон — по номеру вагона; пусто — не назначен
const responsibles = ref<Record<string, number[]>>({})
/** Тип вагона плана — из вагонов поезда рейса. */
const wagonType = computed(
  () => crew.value?.wagons.find((w) => w.number === change.value?.wagon?.number)?.wagonType ?? 0,
)
const loadError = ref<ShownError | null>(null)
const error = ref<ShownError | null>(null)
const running = ref(false)
// шаг мастера: 0 — отметка, 1 — ответственные (только в пути)
const step = ref(0)

async function load() {
  loadError.value = null
  try {
    await busy.run(async () => {
      const [data] = await Promise.all([fetchTripCrew(Number(props.id)), dictManager.prepareDicts('Station')])
      crew.value = data
      if (!change.value) loadError.value = formError(t('План не найден или уже отмечен'))
      comment.value = change.value?.comment ?? ''
    })
  } catch (e) {
    loadError.value = shownError(e)
  }
}
void load()

function goBack() {
  if (window.history.state?.back) router.back()
  else void router.push({ name: 'pwa-home' })
}

const navActions = computed<BottomBarAction[]>(() => {
  const ready = !!change.value && isOnline.value
  if (step.value === 0 && twoSteps.value) {
    return [
      { key: 'back', title: 'Отмена', align: 'left' },
      { key: 'next', title: 'Далее', kind: 'primary', disabled: !ready || !time.value },
    ]
  }
  return [
    { key: 'back', title: step.value === 0 ? 'Отмена' : 'Назад', align: 'left' },
    { key: 'save', title: confirmText.value, kind: 'primary', loading: running.value, disabled: !ready },
  ]
})

function onAction(key: string) {
  if (key === 'back') {
    if (step.value === 0) goBack()
    else step.value = 0
    return
  }
  if (key === 'next') {
    // время проверяется до шага ответственных
    if (!checkTime()) return
    step.value = 1
    return
  }
  void save()
}

/** Время не в будущем; иначе — ошибка на экране. */
function checkTime(): boolean {
  if (!time.value || new Date(time.value).getTime() > Date.now()) {
    error.value = formError(t('Время не может быть в будущем'))
    return false
  }
  error.value = null
  return true
}

async function save() {
  const c = change.value
  if (!c || !time.value || !checkTime()) return
  error.value = null
  running.value = true
  try {
    await markChange(c, {
      time: isoDateTime(new Date(time.value)),
      station: null,
      seq: null,
      comment: comment.value.trim(),
      responsibleUsrs: c.wagon ? (responsibles.value[c.wagon.number] ?? []) : [],
    })
    goBack()
  } catch (e) {
    error.value = shownError(e)
  } finally {
    running.value = false
  }
}
</script>

<style scoped lang="scss">
.mark__bars {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 0.3rem;
}

.mark__bar {
  height: 0.2rem;
  border-radius: 0.1rem;
  background: $zs-border;

  // пройденные шаги и текущий — цветом кнопки
  &--on {
    background: $primary;
  }
}

// как на экране прицепки и отцепки
.mark__field {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.mark__label {
  font-size: 0.9rem;
  font-weight: 600;
}

// подпись — значение, как атрибуты карточки бригады на главной
.mark__attrs {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 0.4rem 0.8rem;
  font-size: 0.9rem;

  > :nth-child(odd) {
    color: $zs-text-muted;
  }
}

.mark__number {
  font-size: 1rem;
  font-weight: 700;
}

.mark__muted {
  color: $zs-text-muted;
  font-size: 0.8rem;
}
</style>
