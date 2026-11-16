<template>
  <PwaPage
    :back="goBack"
    :title="attach ? t('Прицепка вагонов') : t('Отцепка вагонов')"
    :subtitle="card.crew.value ? `${card.crew.value.trip.trainNumber} • ${card.crew.value.trip.name}` : ''"
  >
    <ErrorBox v-if="card.error.value" :error="card.error.value" :retry="card.crew.value ? null : card.load" />
    <LoadSpinner v-if="busy.active.value" />

    <template v-if="card.crew.value">
      <div v-if="!isOnline" class="coupling__muted">{{ t('Для действия нужна связь') }}</div>
      <div class="coupling__field">
        <span class="coupling__label">{{ attach ? t('Остановка прицепки') : t('Остановка отцепки') }}</span>
        <CbList
          :model-value="card.stop.value"
          single
          placeholder="Выберите…"
          :values="card.stops.value"
          @update:model-value="card.setStop(typeof $event === 'number' ? $event : null)"
        />
      </div>

      <div class="coupling__list">
        <label
          v-for="w in card.wagons.value"
          :key="w.number"
          class="coupling__wagon q-py-sm q-px-md"
          :class="{ 'coupling__wagon--off': !w.available }"
        >
          <input v-model="card.selected.value" type="checkbox" :value="w.number" :disabled="!w.available" />
          <span class="coupling__wagon-text">
            <span class="zs-mono coupling__number">{{ w.number }}</span>
            <span class="coupling__muted">
              {{ dictManager.getValue('WagonType', w.wagonType) }} • {{ card.whereText(w) }}
            </span>
          </span>
        </label>
        <div v-if="!busy.active.value && card.wagons.value.length === 0" class="coupling__empty q-py-lg q-px-md">
          {{ attach && !card.station.value ? t('Выберите остановку') : t('Вагонов нет') }}
        </div>
      </div>

      <label class="coupling__field">
        <span class="coupling__label">{{ attach ? t('Время прицепки') : t('Время отцепки') }}</span>
        <DatePicker v-model="card.time.value" with-time :date-text="attach ? 'Время прицепки' : 'Время отцепки'" />
      </label>
      <label class="coupling__field">
        <span class="coupling__label">{{ t('Основания, примечания') }}</span>
        <NoteInput v-model="card.comment.value" placeholder="Приказ, причина" />
      </label>
      <!-- ответственный за вагоны: бригада рейса и сотрудники депо станции остановки -->
      <div class="coupling__field">
        <span class="coupling__label">{{ t('Ответственный за вагоны') }}</span>
        <ResponsiblePicker
          v-model="card.responsible.value"
          :station="card.station.value"
          :trip="Number(id)"
          :disabled="!card.station.value"
        />
      </div>
    </template>

    <template #bar>
      <q-btn
        unelevated
        no-caps
        color="primary"
        class="zs-pwa-btn zs-pwa-btn--lg full-width"
        :disable="!card.canSubmit.value || !isOnline"
        :label="attach ? t('Прицепить') : t('Отцепить')"
        @click="submit"
      />
    </template>
  </PwaPage>
</template>

<script setup lang="ts">
import { Notify } from 'quasar'
import { computed } from 'vue'
import { useRouter } from 'vue-router'

import { t } from '@/boot/i18n'
import NoteInput from '@/shared/components/form/NoteInput.vue'
import CbList from '@/shared/components/choice/CbList.vue'
import DatePicker from '@/shared/components/period/DatePicker.vue'
import { provideBusy } from '@/shared/busy'
import ErrorBox from '@/shared/components/ErrorBox.vue'
import LoadSpinner from '@/shared/components/LoadSpinner.vue'
import { dictManager } from '@/shared/dict/DictManager'
import { isOnline } from '@/shared/network'
import { useCoupling } from '@/shared/trips/useCoupling'
import ResponsiblePicker from '@/shared/wagons/ResponsiblePicker.vue'
import PwaPage from '../components/PwaPage.vue'

/**
 * Прицепка или отцепка в рейсе на телефоне (Ф-АКТ-01): остановка, вагоны, основания и примечания. Нужна связь.
 */
const props = defineProps<{ id: string; kind: string }>()

const router = useRouter()
// общее ожидание страницы: спиннер на месте содержимого — на всю цепочку загрузок
const busy = provideBusy()
const attach = computed(() => props.kind === 'attach')
const card = useCoupling(Number(props.id), props.kind === 'attach', busy)

function goBack() {
  if (window.history.state?.back) router.back()
  else void router.push({ name: 'pwa-home' })
}

void card.load()

async function submit() {
  if (!(await card.submit())) return
  Notify.create({
    message: attach.value ? t('Вагоны прицеплены') : t('Вагоны отцеплены'),
    position: 'bottom',
  })
  goBack()
}
</script>

<style scoped lang="scss">
.coupling__field {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.coupling__label {
  font-size: 0.9rem;
  font-weight: 600;
}

.coupling__list {
  overflow: hidden;
  border: 0.0625rem solid $zs-border;
  border-radius: 0.3rem;
  background: #fff;
}

.coupling__wagon {
  display: flex;
  align-items: center;
  gap: 0.8rem;
  min-height: 3.3rem;
  border-top: 0.0625rem solid $zs-border-row;

  &:first-child {
    border-top: 0;
  }

  &--off {
    opacity: 0.55;
  }
}

.coupling__wagon-text {
  display: flex;
  flex-direction: column;
  gap: 0.1rem;
  min-width: 0;
}

.coupling__number {
  font-size: 1rem;
  font-weight: 700;
}

.coupling__muted {
  color: $zs-text-muted;
  font-size: 0.8rem;
}

.coupling__empty {
  color: $zs-text-muted;
  font-size: 0.9rem;
}
</style>
