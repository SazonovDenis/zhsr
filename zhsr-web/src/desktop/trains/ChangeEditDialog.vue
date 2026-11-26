<template>
  <FormDialog
    v-model:show="show"
    min-height="md"
    :title="title"
    ok-text="Сохранить"
    width="60rem"
    :running="running"
    :error="error"
    @ok="save"
  >
    <!-- план прицепки или отцепки: что будет мешать планам вагона, если сохранить -->
    <template v-if="!plan || problemRows.length > 0" #warnings>
      <WagonProblemList v-if="plan" :rows="problemRows" strip />
      <template v-else>{{ t('У факта исправляются только основания и примечания.') }}</template>
    </template>
    <!-- план и факт — те же поля, что при прицепке и отцепке, заполненные значениями записи. План правится целиком, вид —
         только «Планом»; у факта правятся только основания: время и место факта меняют журналы вагона -->
    <ChangeWhereFields
      ref="whereEl"
      :card="card"
      :groups="groups"
      :station="change.station"
      :fact-allowed="!plan"
      :attach="attach"
      :at="change.dt"
      :plan="plan"
      :initial="initial"
      :fact="plan ? null : change"
      :train-wagon-seq="seqChange"
    />
    <FormField v-if="seqChange" label="Порядок вагонов">
      <div class="change-fact__value">{{ seqText }}</div>
    </FormField>
    <ErrorBox v-if="problemsError" :error="problemsError" />
  </FormDialog>
</template>

<script setup lang="ts">
import { computed, ref, useTemplateRef } from 'vue'

import type { TrainCard, TrainChange } from '@/api/trains'
import { editTrainChange } from '@/api/trains'
import { t } from '@/boot/i18n'
import { provideBusy } from '@/shared/busy'
import ErrorBox from '@/shared/components/ErrorBox.vue'
import FormDialog from '@/shared/components/dialog/FormDialog.vue'
import FormField from '@/shared/components/form/FormField.vue'
import type { ShownError } from '@/shared/errors'
import { formError, shownError } from '@/shared/errors'
import { useWagonProblems } from '@/shared/trains/useWagonProblems'
import WagonProblemList from '@/shared/trains/WagonProblemList.vue'
import { isAttach, isSeq, isWaiting } from '@/shared/trains/trainJournal'
import type { StopGroup } from './attach'
import type { ChangeInitial } from './changeWhere'
import ChangeWhereFields from './ChangeWhereFields.vue'

/**
 * Исправление записи журнала поезда (Ф-ПОЕ-02). Ожидающий план — целиком: место (рейс и остановка или станция), плановое
 * время, у прицепки позиция, основания — теми же полями, что при прицепке и отцепке (ChangeWhereFields), заполненными
 * значениями плана; план смены порядка исправляет TrainWagonSeqDialog. Факт — те же поля ChangeWhereFields, доступны только основания и примечания; у смены порядка ниже — порядок вагонов. Проверки — на сервере. Открывается через openDialog,
 * результат — поезд после изменения.
 */
const props = defineProps<{ card: TrainCard; change: TrainChange; groups: StopGroup[] }>()

const show = defineModel<boolean>('show', { default: false })
// ожидание диалога: спиннер в подвале (FormDialog)
provideBusy()
const emit = defineEmits<{ done: [card: TrainCard] }>()

const plan = computed(() => isWaiting(props.change))
const attach = computed(() => isAttach(props.change))
const seqChange = computed(() => isSeq(props.change))
/** Новый порядок вагонов факта смены порядка: «1 — 12345, 2 — 67890». */
const seqText = computed(() =>
  [...props.change.trainWagonSeqItem]
    .sort((a, b) => a.seq - b.seq)
    .map((i) => `${i.seq} — ${i.wagon.number}`)
    .join(', '),
)
const title = computed(() => {
  if (isSeq(props.change)) return t('Основания смены порядка вагонов')
  return t(
    plan.value
      ? attach.value
        ? 'Исправить план прицепки вагона {n}'
        : 'Исправить план отцепки вагона {n}'
      : attach.value
        ? 'Основания прицепки вагона {n}'
        : 'Основания отцепки вагона {n}',
    { n: props.change.wagon!.number },
  )
})

const initial: ChangeInitial = {
  time: props.change.dt,
  trip: props.change.trip?.id ?? null,
  routeStation: props.change.routeStation,
  station: props.change.station,
  seq: props.change.seq,
  comment: props.change.comment ?? '',
  wagon: props.change.wagon?.id ?? 0,
}

const whereEl = useTemplateRef<InstanceType<typeof ChangeWhereFields>>('whereEl')
const { rows: problemRows, error: problemsError } = useWagonProblems(() => {
  const where = whereEl.value
  if (!plan.value || isSeq(props.change) || !where) return null
  return {
    train: props.change.train.id,
    wagons: [props.change.wagon!.id],
    changeType: props.change.changeType!,
    fact: false,
    time: where.moment,
    station: where.targetStation,
    routeStation: where.routeStation,
    plan: props.change.id,
  }
})
const running = ref(false)
// ошибка формы: проверки (formError) или запроса (shownError, запрос второй строкой); null — ошибки нет
const error = ref<ShownError | null>(null)

async function save() {
  let body
  if (plan.value) {
    const choice = whereEl.value?.build()
    if (!choice) return
    if (typeof choice === 'string') {
      error.value = formError(choice)
      return
    }
    body = { ...choice, wagons: [], fact: false, responsibleUsrs: [] }
  } else {
    body = {
      wagons: [],
      fact: true,
      time: null,
      station: null,
      routeStation: null,
      seq: null,
      comment: whereEl.value!.state.comment.trim(),
      responsibleUsrs: [],
    }
  }
  error.value = null
  running.value = true
  try {
    emit('done', await editTrainChange(props.change, body))
  } catch (e) {
    error.value = shownError(e)
  } finally {
    running.value = false
  }
}
</script>

<style scoped lang="scss">
.change-fact__value {
  color: $zs-text;
  font-weight: 400;
}
</style>
