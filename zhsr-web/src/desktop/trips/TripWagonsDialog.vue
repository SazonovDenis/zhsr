<template>
  <FormDialog
    v-model:show="show"
    title="Добавить вагоны"
    ok-text="Добавить"
    width="38rem"
    hint="Вагоны в депо и парке отстоя перед отправлением нужно передать на станцию."
    :error="error"
    @ok="save"
  >
    <div class="trip-wagons__note">{{ t('Вагоны станции отправления {station}', { station }) }}</div>
    <div class="trip-wagons__table">
      <label v-for="w in wagons" :key="w.number" class="trip-wagons__row">
        <q-checkbox v-model="selected" :val="w.number" dense />
        <span class="zs-mono">{{ w.number }}</span>
        <span>{{ dictManager.getValue('WagonType', w.wagonType) }}</span>
        <span class="zs-sub">{{ placeText(w.locationType, w.place) }}</span>
        <DictBadge :id="w.wagonStatusType" dict="WagonState" />
      </label>
      <div v-if="!busy.active.value && wagons.length === 0" class="trip-wagons__empty">
        {{ t('Свободных вагонов на станции нет') }}
      </div>
    </div>
  </FormDialog>
</template>

<script setup lang="ts">
import { ref, shallowRef } from 'vue'

import type { WagonCandidate } from '@/api/trips'
import { fetchWagonCandidates } from '@/api/trips'
import { t } from '@/boot/i18n'
import { provideBusy } from '@/shared/busy'
import DictBadge from '@/shared/components/DictBadge.vue'
import FormDialog from '@/shared/components/dialog/FormDialog.vue'
import { dictManager } from '@/shared/dict/DictManager'
import type { ShownError } from '@/shared/errors'
import { formError, shownError } from '@/shared/errors'
import { placeText } from '@/shared/wagonPlace'

/**
 * Диалог «Добавить вагоны» (Ф-РЕЙ-02): свободные вагоны на станции отправления, в депо и в парке отстоя при ней.
 * Открывается через openDialog, результат — номера отмеченных вагонов.
 */
const props = defineProps<{ tripId: number; station: string }>()

const show = defineModel<boolean>('show', { default: false })
// ожидание диалога: спиннер в подвале (FormDialog) — на всю цепочку загрузок формы
const busy = provideBusy()
const emit = defineEmits<{ done: [numbers: string[]] }>()

const wagons = shallowRef<WagonCandidate[]>([])
const selected = ref<string[]>([])
const error = ref<ShownError | null>(null)

void busy.run(async () => {
  try {
    const [list] = await Promise.all([
      fetchWagonCandidates(props.tripId),
      dictManager.prepareDicts('WagonType', 'Station', 'Depot', 'Yard'),
    ])
    wagons.value = list
  } catch (e) {
    error.value = shownError(e)
  }
})

function save() {
  if (selected.value.length === 0) {
    error.value = formError(t('Отметьте вагоны'))
    return
  }
  emit('done', selected.value)
}
</script>

<style scoped lang="scss">
.trip-wagons__note {
  margin-bottom: 0.625rem;
  color: $zs-text-muted;
  font-size: 0.8125rem;
}

.trip-wagons__table {
  max-height: 24rem;
  overflow-y: auto;
  border: 0.0625rem solid $zs-border;
  border-radius: 0.25rem;
}

.trip-wagons__row {
  display: grid;
  grid-template-columns: auto 5rem 1fr 1fr auto;
  align-items: center;
  gap: 0.625rem;
  padding: 0.375rem 0.75rem;
  border-top: 0.0625rem solid $zs-border-row;
  font-size: 0.8125rem;
  cursor: pointer;

  &:first-child {
    border-top: 0;
  }
}

.trip-wagons__empty {
  padding: 1rem 0.75rem;
  color: $zs-text-muted;
  font-size: 0.8125rem;
}
</style>
