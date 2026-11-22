<template>
  <FormDialog
    v-model:show="show"
    :title="t(attach ? 'Отметить прицепку вагона {n}' : 'Отметить отцепку вагона {n}', { n: change.wagon.number })"
    ok-text="Отметить"
    :hint="hint"
    :running="running"
    :error="error"
    @ok="save"
  >
    <FormSection columns="max-content max-content">
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
        <CbList v-model="seq" single no-clear :values="seqChoices" style="width: 14rem" />
      </FormField>
    </FormSection>
    <!-- по умолчанию — основания плана -->
    <FormField label="Основания, примечания" class="q-mt-md">
      <NoteInput v-model="comment" :rows="2" />
    </FormField>
    <!-- в рейсе прицепка и отцепка меняют журналы вагона: ответственные выбираются явно -->
    <FormField v-if="inProgress" label="Ответственные за вагон" class="q-mt-md">
      <ResponsiblePicker
        v-model="responsible"
        :station="change.station"
        :trip="change.trip?.id ?? null"
        :wagons="[change.wagon.number]"
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
import DatePicker from '@/shared/components/period/DatePicker.vue'
import { dictChoices, dictSource } from '@/shared/dict/dictChoices'
import { dictManager } from '@/shared/dict/DictManager'
import type { ShownError } from '@/shared/errors'
import { formError, shownError } from '@/shared/errors'
import { formatDateTime, isoDateTime } from '@/shared/format'
import { currentComposition, isAttach } from '@/shared/trains/trainJournal'
import type { ResponsibleChoice } from '@/shared/wagons/responsible'
import { responsibleUsrs } from '@/shared/wagons/responsible'
import ResponsiblePicker from '@/shared/wagons/ResponsiblePicker.vue'

/**
 * Отметка исполнения плана прицепки или отцепки (Ф-ПОЕ-02, Ф-АКТ-01): Система вносит факт по плану, план —
 * «отработан». Время, станцию плана вне рейса, позицию прицепки и основания можно исправить: план сохраняет свои, отклонение
 * видно в журнале. В рейсе в статусе «выполняется» — ещё ответственные за вагон. Позиции — по текущему составу поезда:
 * окно загружает поезд само. Открывается через openDialog, результат — поезд после отметки.
 */
const props = defineProps<{ change: TrainChange; inProgress: boolean }>()

const show = defineModel<boolean>('show', { default: false })
const emit = defineEmits<{ done: [train: TrainCard] }>()

const attach = isAttach(props.change)
const time = ref(date.formatDate(new Date(), 'YYYY-MM-DDTHH:mm'))
const station = ref<number | null>(props.change.station)
const seq = ref<number>(props.change.seq ?? Number.MAX_SAFE_INTEGER)
const comment = ref(props.change.comment ?? '')
const responsible = ref<ResponsibleChoice>(null)
const running = ref(false)
const error = ref<ShownError | null>(null)
const card = shallowRef<TrainCard | null>(null)

const hint = computed(() =>
  t('По плану: {where}, {time}', {
    where: dictManager.getValue('Station', props.change.station),
    time: formatDateTime(props.change.dt),
  }),
)

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
    if (seq.value > size) seq.value = Number.MAX_SAFE_INTEGER
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
  if (props.inProgress && responsible.value === null) {
    error.value = formError(t('Выберите ответственных за вагон'))
    return
  }
  const size = card.value ? currentComposition(card.value).length : 0
  error.value = null
  running.value = true
  try {
    emit(
      'done',
      await markChange(props.change.id, {
        time: isoDateTime(new Date(time.value)),
        station: props.change.trip ? null : station.value,
        seq: attach ? (seq.value <= size ? seq.value : size + 1) : null,
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
