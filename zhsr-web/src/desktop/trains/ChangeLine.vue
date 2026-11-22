<template>
  <span class="change-line">
    <span :class="{ 'change-line__plan': waiting, 'change-line__overdue': change.overdue }">{{ text }}</span>
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
import { changeActions, deviationText, isAttach, isWaiting } from '@/shared/trains/trainJournal'

/**
 * Прицепка или отцепка вагона одной строкой с действиями: план — «прицепка по плану: Астана, 30.10 11:00, на 7» с
 * «Отметить» и «Отменить план»; факт — «прицеплен: Астана, 28.10 06:00» с отклонением от плана и «Удалить факт».
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

const text = computed(() => {
  const c = props.change
  const where = dictManager.getValue('Station', c.station)
  const time = formatDateTime(c.dt)
  const seq = isAttach(c) && c.seq !== null ? `, ${t('на {n}', { n: c.seq })}` : ''
  if (waiting.value) {
    const base = t(isAttach(c) ? 'прицепка по плану: {where}, {time}' : 'отцепка по плану: {where}, {time}', {
      where,
      time,
    })
    return `${base}${seq}${c.overdue ? ` • ${t('просрочен')}` : ''}`
  }
  return `${t(isAttach(c) ? 'прицеплен: {where}, {time}' : 'отцеплен: {where}, {time}', { where, time })}${seq}`
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

.change-line__plan {
  color: $primary;
  font-weight: 600;
}

.change-line__overdue {
  color: $zs-error-fg;
}

.change-line__muted {
  color: $zs-text-faint;
}
</style>
