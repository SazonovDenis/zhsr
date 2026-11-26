<template>
  <FormDialog v-model:show="show" :title="title" ok-text="Отметить" :running="running" :error="error" @ok="save">
    <template v-if="problemRows.length > 0" #warnings>
      <WagonProblemList :rows="problemRows" strip />
    </template>
    <!-- смена порядка: порядок задан планом -->
    <div v-if="seq" class="zs-sub q-mb-md">{{ t('Новый порядок: {list}', { list: changeNumbers(change) }) }}</div>
    <FormSection columns="max-content max-content">
      <!-- план — неактивными полями над фактом; станция по плану — только когда для факта выбрана другая -->
      <FormField label="Плановое время">
        <DatePicker :model-value="planTime" with-time disabled date-text="Плановое время" />
      </FormField>
      <FormField v-if="!change.trip && station !== change.station" label="Станция по плану">
        <CbList
          :model-value="change.station"
          single
          disabled
          no-clear
          :values="dictChoices('Station')"
          :source="dictSource('Station')"
          style="width: 14rem"
        />
      </FormField>
      <!-- пустая ячейка сетки: поля факта начинаются с новой строки -->
      <div v-else />
      <FormField label="Фактическое время">
        <DatePicker v-model="time" with-time date-text="Фактическое время" />
      </FormField>
      <!-- вне рейса станцию можно исправить; в рейсе — станция остановки плана, неактивным полем -->
      <FormField label="Станция">
        <CbList
          v-if="!change.trip"
          v-model="station"
          single
          no-clear
          :values="dictChoices('Station')"
          :source="dictSource('Station')"
          style="width: 14rem"
        />
        <CbList
          v-else
          :model-value="change.station"
          single
          disabled
          no-clear
          :values="dictChoices('Station')"
          :source="dictSource('Station')"
          style="width: 14rem"
        />
      </FormField>
      <!-- позиция — по текущему составу; по умолчанию — позиция плана -->
      <FormField v-if="attach" label="Позиция в поезде">
        <CbList v-model="position" single no-clear :values="seqChoices" style="width: 14rem" />
      </FormField>
    </FormSection>
    <!-- по умолчанию — основания плана -->
    <FormField label="Основания, примечания" class="q-mt-md">
      <NoteInput v-model="comment" :rows="2" />
    </FormField>
    <!-- что будет мешать планам вагона, если отметить -->
    <ErrorBox v-if="problemsError" :error="problemsError" />
    <!-- в рейсе прицепка и отцепка меняют журналы вагона: ответственные выбираются явно -->
    <FormField v-if="withResponsible" label="Ответственные за вагон" class="q-mt-md">
      <ResponsiblePicker
        v-model="responsible"
        :station="change.station"
        :trip="change.trip?.id ?? null"
        :wagons="changeWagons(change)"
      />
    </FormField>
  </FormDialog>
</template>

<script setup lang="ts">
import { date } from 'quasar'
import { computed, ref, shallowRef } from 'vue'

import type { TrainCard, TrainChange } from '@/api/trains'
import { fetchTrain, markChange } from '@/api/trains'
import { t } from '@/boot/i18n'
import CbList from '@/shared/components/choice/CbList.vue'
import type { ChoiceItem } from '@/shared/components/choice/choice'
import FormDialog from '@/shared/components/dialog/FormDialog.vue'
import FormField from '@/shared/components/form/FormField.vue'
import FormSection from '@/shared/components/form/FormSection.vue'
import NoteInput from '@/shared/components/form/NoteInput.vue'
import ErrorBox from '@/shared/components/ErrorBox.vue'
import DatePicker from '@/shared/components/period/DatePicker.vue'
import { dictChoices, dictSource } from '@/shared/dict/dictChoices'
import type { ShownError } from '@/shared/errors'
import { formError, shownError } from '@/shared/errors'
import { formatDateTime, isoDateTime } from '@/shared/format'
import { useWagonProblems } from '@/shared/trains/useWagonProblems'
import WagonProblemList from '@/shared/trains/WagonProblemList.vue'
import { changeNumbers, changeWagons, currentComposition, isAttach, isSeq } from '@/shared/trains/trainJournal'
import type { ResponsibleChoice } from '@/shared/wagons/responsible'
import { responsibleUsrs } from '@/shared/wagons/responsible'
import ResponsiblePicker from '@/shared/wagons/ResponsiblePicker.vue'

/**
 * Отметка исполнения плана прицепки, отцепки или смены порядка вагонов (Ф-ПОЕ-02, Ф-АКТ-01): Система вносит факт по
 * плану (у смены порядка — порядок плана, без позиции и ответственных), план —
 * «отработан». Время, станцию плана вне рейса, позицию прицепки и основания можно исправить: план сохраняет свои, отклонение
 * видно в журнале. В рейсе в статусе «выполняется» — ещё ответственные за вагон. Позиции — по текущему составу поезда:
 * окно загружает поезд само. Открывается через openDialog, результат — поезд после отметки.
 */
const props = defineProps<{ change: TrainChange; inProgress: boolean }>()

const show = defineModel<boolean>('show', { default: false })
const emit = defineEmits<{ done: [train: TrainCard] }>()

const attach = isAttach(props.change)
const seq = isSeq(props.change)
// прицепка и отцепка в рейсе в пути меняют журналы вагона; смена порядка — нет
const withResponsible = props.inProgress && !seq
const title = computed(() => {
  if (seq) return t('Отметить смену порядка вагонов')
  return t(attach ? 'Отметить прицепку вагона {n}' : 'Отметить отцепку вагона {n}', { n: props.change.wagon!.number })
})
const time = ref(date.formatDate(new Date(), 'YYYY-MM-DDTHH:mm'))
const planTime = date.formatDate(new Date(props.change.dt), 'YYYY-MM-DDTHH:mm')
const station = ref<number | null>(props.change.station)
const position = ref<number>(props.change.seq ?? Number.MAX_SAFE_INTEGER)
const comment = ref(props.change.comment ?? '')
const responsible = ref<ResponsibleChoice>(null)
const running = ref(false)
const error = ref<ShownError | null>(null)
const card = shallowRef<TrainCard | null>(null)

const { rows: problemRows, error: problemsError } = useWagonProblems(() => {
  if (seq) return null
  const at = new Date(time.value)
  return {
    train: props.change.train.id,
    wagons: [props.change.wagon!.id],
    changeType: props.change.changeType!,
    fact: true,
    time: Number.isNaN(at.getTime()) ? null : isoDateTime(at),
    station: props.change.trip ? props.change.station : station.value,
    routeStation: props.change.routeStation,
    plan: props.change.id,
  }
})

/** Позиции прицепки по текущему составу: «1 — перед 12345» … «в конец». */
const seqChoices = computed<ChoiceItem[]>(() => {
  const list = card.value ? currentComposition(card.value) : []
  return [
    ...list.map((w, i) => ({ id: i + 1, text: t('{n} — перед {number}', { n: i + 1, number: w.wagon.number }) })),
    { id: Number.MAX_SAFE_INTEGER, text: t('в конец') },
  ]
})

async function load() {
  try {
    card.value = await fetchTrain(props.change.train.id)
    const size = currentComposition(card.value).length
    if (position.value > size) position.value = Number.MAX_SAFE_INTEGER
  } catch (e) {
    error.value = shownError(e)
  }
}
void load()

async function save() {
  // время — ошибкой диалога, под датой подписей нет
  const at = new Date(time.value).getTime()
  if (at > Date.now()) {
    error.value = formError(t('Время не может быть в будущем'))
    return
  }
  const last = card.value?.lastFactDt
  if (last && at < new Date(last).getTime()) {
    error.value = formError(t('Время не раньше {time}', { time: formatDateTime(last) }))
    return
  }
  if (withResponsible && responsible.value === null) {
    error.value = formError(t('Выберите ответственных за вагон'))
    return
  }
  const size = card.value ? currentComposition(card.value).length : 0
  error.value = null
  running.value = true
  try {
    emit(
      'done',
      await markChange(props.change, {
        time: isoDateTime(new Date(time.value)),
        station: props.change.trip ? null : station.value,
        seq: attach ? (position.value <= size ? position.value : size + 1) : null,
        comment: comment.value.trim(),
        responsibleUsrs: responsibleUsrs(responsible.value),
      }),
    )
  } catch (e) {
    error.value = shownError(e)
  } finally {
    running.value = false
  }
}
</script>
