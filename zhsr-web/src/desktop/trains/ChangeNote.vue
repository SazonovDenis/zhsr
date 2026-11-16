<template>
  <span class="change-note">
    <span :class="{ 'change-note__plan': planned }">{{ text }}</span>
    <template v-if="planned">
      <q-btn
        flat
        dense
        no-caps
        color="primary"
        padding="0"
        :disable="!change.canMark"
        :title="change.canMark ? undefined : t(MARK_REASON)"
        @click="emit('mark', change)"
      >
        {{ t('Отметить') }}
      </q-btn>
      <q-btn
        flat
        dense
        no-caps
        color="primary"
        padding="0"
        :disable="!change.canMark"
        :title="change.canMark ? undefined : t(MARK_REASON)"
        @click="emit('cancel', change)"
      >
        {{ t('Отменить план') }}
      </q-btn>
    </template>
    <q-btn
      v-else-if="undoable"
      flat
      dense
      no-caps
      color="primary"
      padding="0"
      :disable="!change.canUndo"
      :title="change.canUndo ? undefined : t(UNDO_REASON)"
      @click="emit('undo', change)"
    >
      {{ t('Отменить') }}
    </q-btn>
  </span>
</template>

<script setup lang="ts">
import { computed } from 'vue'

import type { TrainChange } from '@/api/trains'
import { t } from '@/boot/i18n'
import { TrainWagonChangeStatus, TrainWagonChangeType } from '@/shared/constants/TrainWagonChange'
import { dictManager } from '@/shared/dict/DictManager'
import { formatDateTime } from '@/shared/format'

/**
 * Прицепка или отцепка вагона одной строкой: «прицепка по плану: Астана-1, 12.11 08:00» с кнопками «Отметить» и
 * «Отменить план» или «прицеплен: Астана-1, 12.11 08:00» с кнопкой «Отменить» (undoable). Кнопки видны всегда; без
 * прав — неактивны, причина во всплывающей подсказке. Станции — справочник Station: его готовит страница.
 */
const props = withDefaults(defineProps<{ change: TrainChange; undoable?: boolean }>(), { undoable: false })
const emit = defineEmits<{ mark: [change: TrainChange]; cancel: [change: TrainChange]; undo: [change: TrainChange] }>()

const MARK_REASON = 'Отмечает и отменяет план планировщик вагонов, в рейсе — ЛНП рейса'
const UNDO_REASON = 'Отменяет планировщик вагонов, в рейсе — ЛНП рейса'

const planned = computed(() => props.change.status === TrainWagonChangeStatus.PLANNED)

const text = computed(() => {
  const c = props.change
  const attach = c.changeType === TrainWagonChangeType.ATTACH
  const where = dictManager.getValue('Station', c.station)
  if (planned.value) {
    return t(attach ? 'прицепка по плану: {where}, {time}' : 'отцепка по плану: {where}, {time}', {
      where,
      time: formatDateTime(c.plannedDt),
    })
  }
  return t(attach ? 'прицеплен: {where}, {time}' : 'отцеплен: {where}, {time}', {
    where,
    time: formatDateTime(c.factDt),
  })
})
</script>

<style scoped lang="scss">
.change-note {
  display: inline-flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.5rem;
  color: $zs-text-muted;
  font-size: 0.8rem;
}

.change-note__plan {
  color: $primary;
  font-weight: 600;
}
</style>
