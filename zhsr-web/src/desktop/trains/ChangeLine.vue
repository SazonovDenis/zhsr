<template>
  <span class="change-line">
    <span :class="{ 'change-line__overdue': change.overdue }">{{ kind }}{{ text }}</span>
    <span v-if="deviation" class="change-line__muted">{{ deviation }}</span>
    <q-btn
      v-for="a in actions"
      :key="a.label"
      flat
      dense
      no-caps
      color="primary"
      padding="0"
      :disable="!!a.reason || disable"
      :title="a.reason || undefined"
      @click="a.run"
    >
      {{ t(a.label) }}
    </q-btn>
  </span>
</template>

<script setup lang="ts">
import { computed } from 'vue'

import type { TrainChange } from '@/api/trains'
import { t } from '@/boot/i18n'
import { dictManager } from '@/shared/dict/DictManager'
import { formatDateTime } from '@/shared/format'
import { changeActions, deviationText, isAttach, isSeq, isWaiting } from '@/shared/trains/trainJournal'

/**
 * Прицепка или отцепка вагона одной строкой с действиями: план — «прицепка по плану: Астана, 30.10 11:00, на 7» с
 * «Отметить выполнение» и «Отменить план»; факт — «прицеплен: Астана, 28.10 06:00» с отклонением от плана и «Удалить факт».
 * Смена порядка — «смена порядка по плану» и «порядок вагонов изменён».
 * Кнопки видны всегда; недоступные — неактивны, причина во всплывающей подсказке. Справочник Station готовит страница.
 *
 * @prop change     запись журнала
 * @prop facts      известные факты поезда или рейса: факт не удалить, пока после него есть факты того же вагона
 * @prop disable    идёт другое действие
 */
const props = withDefaults(defineProps<{ change: TrainChange; facts?: TrainChange[]; disable?: boolean }>(), {
  facts: () => [],
  disable: false,
})
const emit = defineEmits<{ mark: [change: TrainChange]; cancel: [change: TrainChange]; undo: [change: TrainChange] }>()

const waiting = computed(() => isWaiting(props.change))
const deviation = computed(() => deviationText(props.change))

/** Слово вида: план — «прицепка по плану», факт — «прицеплен». */
const kind = computed(() => {
  if (isSeq(props.change)) return waiting.value ? t('смена порядка по плану') : t('порядок вагонов изменён')
  const attach = isAttach(props.change)
  if (waiting.value) return attach ? t('прицепка по плану') : t('отцепка по плану')
  return attach ? t('прицеплен') : t('отцеплен')
})

/** После слова вида: «: Астана, 30.10 11:00, на 7», у просроченного плана — «• просрочен». */
const text = computed(() => {
  const c = props.change
  const where = dictManager.getValue('Station', c.station)
  const time = formatDateTime(c.dt)
  const seq = isAttach(c) && c.seq !== null ? `, ${t('на {n}', { n: c.seq })}` : ''
  const overdue = waiting.value && c.overdue ? ` • ${t('просрочен')}` : ''
  return `: ${where}, ${time}${seq}${overdue}`
})

const actions = computed(() =>
  changeActions(props.change, props.facts, {
    mark: (c) => emit('mark', c),
    cancel: (c) => emit('cancel', c),
    undo: (c) => emit('undo', c),
  }),
)
</script>

<style scoped lang="scss">
.change-line {
  display: inline-flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.5rem;
  color: $zs-text-muted;
  font-size: 0.8rem;
}

.change-line__overdue {
  color: $zs-error-fg;
}

.change-line__muted {
  color: $zs-text-faint;
}
</style>
