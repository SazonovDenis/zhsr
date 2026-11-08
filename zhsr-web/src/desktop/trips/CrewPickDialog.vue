<template>
  <FormDialog
    v-model:show="show"
    title="Проводники рейса"
    ok-text="Сохранить"
    width="38rem"
    hint="Снятый галочкой проводник уходит из бригады и с вагона."
    :running="running"
    :error="error"
    @ok="save"
  >
    <SidePanelFields>
      <span>{{ t('Рейс') }}</span>
      <span>{{ tripName }}</span>
      <span>{{ t('Станция отправления') }}</span>
      <span class="crew-pick__station">{{ station }}</span>
    </SidePanelFields>

    <div class="crew-pick__table">
      <div class="crew-pick__head">
        <span />
        <span>{{ t('Проводник') }}</span>
        <span>{{ t('Сейчас') }}</span>
      </div>
      <label v-for="c in candidates" :key="c.id" class="crew-pick__row q-py-sm q-px-md">
        <q-checkbox v-model="selected" :val="c.id" dense />
        <span>
          {{ userName(c) }}
          <span v-if="c.senior" class="zs-sub">{{ t('ст.') }}</span>
        </span>
        <span class="zs-sub">{{ busyText(c) }}</span>
      </label>
      <div v-if="!loading && candidates.length === 0" class="crew-pick__empty q-pa-md">{{ t('Проводников нет') }}</div>
    </div>
    <div class="crew-pick__count">{{ t('Отмечено: {n}', { n: selected.length }) }}</div>
  </FormDialog>
</template>

<script setup lang="ts">
import { ref, shallowRef } from 'vue'

import type { ConductorCandidate } from '@/api/trips'
import { fetchConductorCandidates, setTripConductors } from '@/api/trips'
import { t } from '@/boot/i18n'
import type { ShownError } from '@/shared/errors'
import { shownError } from '@/shared/errors'
import { provideBusy } from '@/shared/busy'
import FormDialog from '@/shared/components/dialog/FormDialog.vue'
import { formatDateTime } from '@/shared/format'
import SidePanelFields from '../frame/SidePanelFields.vue'
import { tripRefText } from '@/shared/trips/tripRefText'
import { userName } from '@/shared/user'

/**
 * Диалог «Проводники…» (Ф-РЕЙ-06): планировщик проводников набирает проводников рейса. Сверху — рейс и станция отправления, ниже
 * — проводники станции отправления и уже назначенные в рейс; назначенные отмечены галочками. Проводник, занятый в
 * другом рейсе, показан, если освободится к отправлению: «в рейсе … • свободен с …». Новые проводники попадают в
 * нераспределённые, снятые — уходят из бригады и с вагона. Открывается через openDialog, результат — true.
 */
const props = defineProps<{ tripId: number; tripName: string; station: string }>()

const show = defineModel<boolean>('show', { default: false })
// ожидание диалога: спиннер в подвале (FormDialog) — на всю цепочку загрузок формы
const busy = provideBusy()
const emit = defineEmits<{ done: [saved: boolean] }>()

const candidates = shallowRef<ConductorCandidate[]>([])
const selected = ref<number[]>([])
const loading = ref(true)
const running = ref(false)
// ошибка формы: проверки (formError) или запроса (shownError, запрос второй строкой); null — ошибки нет
const error = ref<ShownError | null>(null)

// загрузка формы — сразу в setup и в счёт ожидания диалога: спиннер в подвале с первой отрисовки
void busy.run(async () => {
  try {
    candidates.value = await fetchConductorCandidates(props.tripId)
    selected.value = candidates.value.filter((c) => c.assigned).map((c) => c.id)
  } catch (e) {
    error.value = shownError(e)
  } finally {
    loading.value = false
  }
})

function busyText(c: ConductorCandidate): string {
  if (!c.busyUntil) return t('свободен')
  return t('в рейсе {trip} • свободен с {until}', {
    trip: c.busyTrip ? tripRefText(c.busyTrip) : '',
    until: formatDateTime(c.busyUntil),
  })
}

async function save() {
  error.value = null
  running.value = true
  try {
    await setTripConductors(props.tripId, selected.value)
    emit('done', true)
  } catch (e) {
    error.value = shownError(e)
  } finally {
    running.value = false
  }
}
</script>

<style scoped lang="scss">
.crew-pick__station {
  font-weight: 600;
}

.crew-pick__table {
  max-height: 22rem;
  overflow-y: auto;
  border: 0.0625rem solid $zs-border;
  border-radius: 0.3rem;
  font-size: 0.8rem;
}

.crew-pick__head,
.crew-pick__row {
  display: grid;
  grid-template-columns: 2rem minmax(0, 1fr) minmax(0, 1.4fr);
  gap: 0.5rem;
  align-items: center;
}

.crew-pick__head {
  position: sticky;
  top: 0;
  z-index: 1;
  border-bottom: 0.0625rem solid $zs-table-head-border;
  background: $zs-table-head-bg;
  color: $zs-table-head-fg;
  font-size: 0.8rem;
  font-weight: 600;
}

.crew-pick__row {
  border-bottom: 0.0625rem solid $zs-border-row;
  cursor: pointer;

  &:hover {
    background: $zs-row-hover-bg;
  }
}

.crew-pick__empty {
  color: $zs-text-muted;
}

.crew-pick__count {
  color: $zs-text-muted;
  font-size: 0.8rem;
}
</style>
