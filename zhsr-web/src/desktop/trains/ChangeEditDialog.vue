<template>
  <FormDialog
    v-model:show="show"
    :height="plan ? 'md' : 'sm'"
    :title="title"
    ok-text="Сохранить"
    :width="plan ? '60rem' : '36rem'"
    :running="running"
    :error="error"
    @ok="save"
  >
    <!-- план — те же поля, что при прицепке и отцепке, заполненные значениями плана; вид — только «Планом» -->
    <ChangeWhereFields
      v-if="plan"
      ref="whereEl"
      :card="card"
      :groups="groups"
      :station="change.station"
      :fact-allowed="false"
      :attach="attach"
      :at="change.dt"
      plan
      :initial="initial"
    />
    <!-- факт — только основания: время и место факта меняют журналы вагона -->
    <template v-else>
      <div class="zs-sub">{{ t('У факта исправляются только основания и примечания.') }}</div>
      <FormField label="Основания, примечания">
        <NoteInput v-model="comment" :rows="3" />
      </FormField>
    </template>
  </FormDialog>
</template>

<script setup lang="ts">
import { computed, ref, useTemplateRef } from 'vue'

import type { TrainCard, TrainChange } from '@/api/trains'
import { editTrainChange } from '@/api/trains'
import { t } from '@/boot/i18n'
import { provideBusy } from '@/shared/busy'
import FormDialog from '@/shared/components/dialog/FormDialog.vue'
import FormField from '@/shared/components/form/FormField.vue'
import NoteInput from '@/shared/components/form/NoteInput.vue'
import type { ShownError } from '@/shared/errors'
import { formError, shownError } from '@/shared/errors'
import { isAttach, isWaiting } from '@/shared/trains/trainJournal'
import type { StopGroup } from './attach'
import type { ChangeInitial } from './changeWhere'
import ChangeWhereFields from './ChangeWhereFields.vue'

/**
 * Исправление записи журнала поезда (Ф-ПОЕ-02). Ожидающий план — целиком: место (рейс и остановка или станция), плановое
 * время, у прицепки позиция, основания — теми же полями, что при прицепке и отцепке (ChangeWhereFields), заполненными
 * значениями плана. Факт — только основания и примечания. Проверки — на сервере. Открывается через openDialog,
 * результат — поезд после изменения.
 */
const props = defineProps<{ card: TrainCard; change: TrainChange; groups: StopGroup[] }>()

const show = defineModel<boolean>('show', { default: false })
// ожидание диалога: спиннер в подвале (FormDialog)
provideBusy()
const emit = defineEmits<{ done: [card: TrainCard] }>()

const plan = computed(() => isWaiting(props.change))
const attach = computed(() => isAttach(props.change))
const title = computed(() =>
  t(
    plan.value
      ? attach.value
        ? 'Исправить план прицепки вагона {n}'
        : 'Исправить план отцепки вагона {n}'
      : attach.value
        ? 'Основания прицепки вагона {n}'
        : 'Основания отцепки вагона {n}',
    { n: props.change.wagon.number },
  ),
)

const initial: ChangeInitial = {
  time: props.change.dt,
  trip: props.change.trip?.id ?? null,
  routeStation: props.change.routeStation,
  station: props.change.station,
  seq: props.change.seq,
  comment: props.change.comment ?? '',
  wagon: props.change.wagon.id,
}

const whereEl = useTemplateRef<InstanceType<typeof ChangeWhereFields>>('whereEl')
const comment = ref(props.change.comment ?? '')
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
      comment: comment.value.trim(),
      responsibleUsrs: [],
    }
  }
  error.value = null
  running.value = true
  try {
    emit('done', await editTrainChange(props.change.id, body))
  } catch (e) {
    error.value = shownError(e)
  } finally {
    running.value = false
  }
}
</script>
