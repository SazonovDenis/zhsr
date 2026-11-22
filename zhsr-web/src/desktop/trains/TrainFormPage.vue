<template>
  <DesktopFrame v-model:floating="floating" :back="back" :right-open="true">
    <template #header>
      <h1 class="desktop-frame__title">{{ card ? t('Поезд {name}', { name: card.name }) : t('Поезд') }}</h1>
      <div v-if="card" class="desktop-frame__actions">
        <q-btn
          unelevated
          outline
          no-caps
          color="primary"
          class="zs-soft-border"
          :disable="!!detachReason || actions.running.value"
          :title="detachReason || undefined"
          :label="t('Отцепить вагоны')"
          @click="actions.detach()"
        />
        <q-btn
          unelevated
          no-caps
          color="primary"
          :disable="!!attachReason || actions.running.value"
          :title="attachReason || undefined"
          :label="t('Прицепить вагоны')"
          @click="actions.attach"
        />
      </div>
    </template>

    <ErrorBox v-if="error" :error="error" :retry="card ? null : load" class="q-mb-md" />
    <LoadSpinner v-if="busy.active.value && !card" />

    <div v-if="card" class="train-form q-pb-md">
      <TrainJournalList
        v-model:side="side"
        :card="card"
        :selected="selected"
        :disable="actions.running.value"
        @select="selected = $event"
        @mark="actions.mark"
        @cancel="actions.cancel"
        @undo="actions.undo"
      />
      <TrainMomentView
        :card="card"
        :change-id="selected"
        :future="side === 'future'"
        :couple-reason="detachReason"
        @detach="(w, fact, plan, at) => void actions.detach(w, fact, plan, at)"
      />
    </div>

    <template #right>
      <TrainPanel
        :train="card"
        form-page
        :closable="false"
        :edit-reason="card && !card.canEdit ? t('Поезд меняет планировщик вагонов') : ''"
        @edit="rename"
      />
    </template>
  </DesktopFrame>
</template>

<script setup lang="ts">
import { computed, ref, shallowRef } from 'vue'
import { useRouter } from 'vue-router'

import type { TrainCard, TrainRow } from '@/api/trains'
import { fetchTrain } from '@/api/trains'
import { t } from '@/boot/i18n'
import { provideBusy } from '@/shared/busy'
import { openDialog } from '@/shared/components/dialog/openDialog'
import ErrorBox from '@/shared/components/ErrorBox.vue'
import LoadSpinner from '@/shared/components/LoadSpinner.vue'
import { dictManager } from '@/shared/dict/DictManager'
import type { ShownError } from '@/shared/errors'
import { shownError } from '@/shared/errors'
import { isoDateTime } from '@/shared/format'
import DesktopFrame from '../frame/DesktopFrame.vue'
import type { JournalSide } from './journalSide'
import TrainJournalList from './TrainJournalList.vue'
import TrainMomentView from './TrainMomentView.vue'
import TrainNameDialog from './TrainNameDialog.vue'
import TrainPanel from './TrainPanel.vue'
import { useTrainActions } from './useTrainActions'

/**
 * Состав поезда (Ф-ПОЕ-02) по технике «журнал и состояние»: в середине — состав на момент (по умолчанию — сейчас, по
 * факту); слева — журнал прицепок и отцепок (виден всегда): прошлое —
 * факты, настоящее — текущее состояние и ожидающие планы. Выбор записи журнала показывает состав на её время: у факта — по факту, у плана —
 * ожидаемый. Прицепка и отцепка нескольких вагонов — кнопками в шапке, отцепка вагона — меню вагона, отметка и отмена
 * плана, удаление ошибочного факта — меню записи журнала. Справа — панель поезда с кнопкой «Редактировать»
 * (обозначение). Проверки — на сервере; здесь только доступность кнопок.
 */
const props = defineProps<{ id: string }>()

const router = useRouter()
const busy = provideBusy()
const trainId = computed(() => Number(props.id))
const floating = ref(false)

const card = shallowRef<TrainCard | null>(null)
const error = ref<ShownError | null>(null)
// выбранная запись журнала — момент состава; null — сейчас
const selected = ref<number | null>(null)
// журнал по умолчанию — настоящее: текущее состояние и ожидающие планы
const side = ref<JournalSide>('future')

const back = () => void router.push({ name: 'trains' })

/** Почему не прицепить: нет прав; пусто — можно. Прицепка к расформированному поезду снова открывает его. */
const attachReason = computed(() => {
  const c = card.value
  if (!c || c.canCouple) return ''
  return c.dend
    ? t('Прицепляет вагоны к расформированному поезду планировщик вагонов')
    : t('Прицепляет и отцепляет планировщик вагонов, у поезда в рейсе — ещё ЛНП рейса и мастер депо')
})

/** Почему не отцепить: поезд расформирован (вагонов нет) или нет прав; пусто — можно. */
const detachReason = computed(() =>
  card.value?.dend ? t('Поезд расформирован: в нём нет вагонов') : attachReason.value,
)

async function load() {
  error.value = null
  try {
    await busy.run(async () => {
      const [data] = await Promise.all([
        fetchTrain(trainId.value),
        dictManager.prepareDicts('Station', 'Depot', 'Yard', 'WagonType', 'WagonModel', 'LocationKind', 'TripState'),
      ])
      card.value = data
    })
  } catch (e) {
    error.value = shownError(e)
  }
}
void load()

/** Состав изменился: ответ сервера — поезд после изменения; null — перечитать. Выбранной записи может не стать. */
async function changed(next: TrainCard | null) {
  error.value = null
  if (next) card.value = next
  else await load()
  if (selected.value !== null && !card.value?.changes.some((c) => c.id === selected.value)) selected.value = null
}

const actions = useTrainActions({
  card: () => Promise.resolve(card.value!),
  at: () => isoDateTime(new Date()),
  trip: () => null,
  // card.trip — рейс поезда в статусе «выполняется»
  inProgress: (c) => !!c.trip && card.value?.trip?.id === c.trip.id,
  changed,
  failed: (e) => (error.value = shownError(e)),
})

async function rename() {
  const tr = card.value!
  const saved = await openDialog<TrainRow>(TrainNameDialog, { train: { id: tr.id, name: tr.name } })
  if (saved) await load()
}
</script>

<style scoped lang="scss">
.train-form {
  display: grid;
  flex: 1;
  grid-template-columns: 28rem minmax(0, 1fr);
  gap: 1rem;
  min-height: 0;
}
</style>
