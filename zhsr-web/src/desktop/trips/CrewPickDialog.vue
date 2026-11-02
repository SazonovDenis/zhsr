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
      <label v-for="c in candidates" :key="c.user" class="crew-pick__row">
        <q-checkbox v-model="selected" :val="c.user" dense />
        <span>
          {{ c.name }}
          <span v-if="c.senior" class="zs-sub">{{ t('ст.') }}</span>
        </span>
        <span class="zs-sub">{{ c.busy ?? t('свободен') }}</span>
      </label>
      <div v-if="!loading && candidates.length === 0" class="crew-pick__empty">{{ t('Проводников нет') }}</div>
    </div>
    <div class="crew-pick__count">{{ t('Отмечено: {n}', { n: selected.length }) }}</div>
  </FormDialog>
</template>

<script setup lang="ts">
import { onMounted, ref, shallowRef } from 'vue'

import type { ConductorCandidate } from '@/api/trips'
import { fetchConductorCandidates, setTripConductors } from '@/api/trips'
import { t } from '@/boot/i18n'
import FormDialog from '@/shared/components/dialog/FormDialog.vue'
import SidePanelFields from '../frame/SidePanelFields.vue'

/**
 * Диалог «Проводники…» (Ф-РЕЙ-06): планировщик набирает проводников рейса. Сверху — рейс и станция отправления, ниже
 * — проводники станции отправления и уже назначенные в рейс; назначенные отмечены галочками. Проводник, занятый в
 * другом рейсе, показан, если освободится к отправлению: «в рейсе … • свободен с …». Новые проводники попадают в
 * нераспределённые, снятые — уходят из бригады и с вагона. Открывается через openDialog, результат — true.
 */
const props = defineProps<{ tripId: string; tripName: string; station: string }>()

const show = defineModel<boolean>('show', { default: false })
const emit = defineEmits<{ done: [saved: boolean] }>()

const candidates = shallowRef<ConductorCandidate[]>([])
const selected = ref<string[]>([])
const loading = ref(true)
const running = ref(false)
const error = ref('')

onMounted(async () => {
  try {
    candidates.value = await fetchConductorCandidates(props.tripId)
    selected.value = candidates.value.filter((c) => c.assigned).map((c) => c.user)
  } catch (e) {
    error.value = e instanceof Error ? t(e.message) : String(e)
  } finally {
    loading.value = false
  }
})

async function save() {
  error.value = ''
  running.value = true
  try {
    await setTripConductors(props.tripId, selected.value)
    emit('done', true)
  } catch (e) {
    error.value = e instanceof Error ? t(e.message) : String(e)
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
  border-radius: 0.25rem;
  font-size: 0.84375rem;
}

.crew-pick__head,
.crew-pick__row {
  display: grid;
  grid-template-columns: 2rem minmax(0, 1fr) minmax(0, 1.4fr);
  gap: 0.5rem;
  align-items: center;
  padding: 0.375rem 0.75rem;
}

.crew-pick__head {
  position: sticky;
  top: 0;
  z-index: 1;
  border-bottom: 0.0625rem solid $zs-table-head-border;
  background: $zs-table-head-bg;
  color: $zs-table-head-fg;
  font-size: 0.75rem;
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
  padding: 0.75rem;
  color: $zs-text-muted;
}

.crew-pick__count {
  color: $zs-text-muted;
  font-size: 0.8125rem;
}
</style>
