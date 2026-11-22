<template>
  <div
    class="journal-item q-py-sm q-pl-md q-pr-xs"
    :class="{ 'journal-item--selected': selected, 'journal-item--plan': plan }"
    role="button"
    tabindex="0"
    @click="emit('select')"
    @keydown.enter.prevent="emit('select')"
    @contextmenu.prevent="hasMenu && emit('menu', $event)"
  >
    <!-- строка «сейчас»: как запись, время идёт само, выделена только жирным -->
    <template v-if="!change">
      <span class="journal-item__time journal-item__now">{{ formatDateTime(new Date(nowMinute).toISOString()) }}</span>
      <span class="journal-item__now">{{ t('Текущее состояние') }}</span>
    </template>
    <template v-else>
      <span class="journal-item__time">{{ hideTime ? '' : formatDateTime(change.dt) }}</span>
      <span class="journal-item__main">
        <span class="journal-item__what">
          <span :class="attach ? 'journal-item__attach' : 'journal-item__detach'">{{ attach ? '+' : '−' }}</span>
          {{ changeKindText(change) }}
          <span class="zs-mono journal-item__number">{{ change.wagon.number }}</span>
          <!-- отступ классом: пробел между номером и текстом шаблон сжимает -->
          <span v-if="attach && change.seq !== null" class="q-ml-xs">{{ t('на {n}', { n: change.seq }) }}</span>
        </span>
        <span>{{ t('Станция: {station}', { station: dictManager.getValue('Station', change.station) }) }}</span>
        <!-- рейс — своей строкой под станцией -->
        <span v-if="change.trip">{{ t('Рейс: {trip}', { trip: tripRefText(change.trip) }) }}</span>
        <span v-if="deviation" class="journal-item__muted">{{ deviation }}</span>
        <span v-if="change.overdue" class="journal-item__overdue">{{ t('просрочен: не отмечен и не отменён') }}</span>
        <span v-if="change.comment" class="journal-item__muted">{{ change.comment }}</span>
      </span>
    </template>
    <button
      v-if="hasMenu"
      type="button"
      class="journal-item__menu q-pa-none"
      :title="t('Действия')"
      :aria-label="t('Действия')"
      @click.stop="emit('menu', $event)"
    >
      <q-icon name="more_vert" size="1.2rem" />
    </button>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

import type { TrainChange } from '@/api/trains'
import { t } from '@/boot/i18n'
import { dictManager } from '@/shared/dict/DictManager'
import { formatDateTime } from '@/shared/format'
import { nowMinute } from '@/shared/since'
import { changeKindText, deviationText, isAttach, isWaiting } from '@/shared/trains/trainJournal'
import { tripRefText } from '@/shared/trips/tripRefText'

/**
 * Запись журнала поезда: время, «+ Прицепка 12345 на 3» или «− Отцепка 12345», где («Станция: Астана», в рейсе ниже
 * строкой — «Рейс: …»), отклонение от плана, просрочка, основания серым. План — цветом плана. Без записи (change null) —
 * строка «Текущее состояние» с текущим временем, которое идёт само. hideTime — время то же, что у записи выше: не
 * показывается. Клик выбирает запись, три точки и правая кнопка — меню действий. Справочник Station готовит страница.
 */
const props = defineProps<{ change: TrainChange | null; selected: boolean; hasMenu?: boolean; hideTime?: boolean }>()
const emit = defineEmits<{ select: []; menu: [e: MouseEvent] }>()

const attach = computed(() => !!props.change && isAttach(props.change))
const plan = computed(() => !!props.change && isWaiting(props.change))
const deviation = computed(() => (props.change ? deviationText(props.change) : ''))
</script>

<style scoped lang="scss">
.journal-item {
  display: grid;
  grid-template-columns: 5.6rem minmax(0, 1fr) auto;
  gap: 0.6rem;
  align-items: start;
  border-top: 0.0625rem solid $zs-border-row;
  border-left: 0.2rem solid transparent;
  font-size: 0.8rem;
  cursor: pointer;

  &:hover {
    background: $zs-row-hover-bg;
  }

  &--selected {
    border-left-color: $zs-row-selected-bar;
    background: $zs-row-selected-bg;
  }
}

.journal-item__time {
  color: $zs-text-muted;
  white-space: nowrap;
}

.journal-item__now {
  font-weight: 700;
}

.journal-item__main {
  display: flex;
  flex-direction: column;
  gap: 0.1rem;
  min-width: 0;
}

.journal-item__what {
  font-weight: 600;
}

.journal-item--plan .journal-item__what {
  color: $primary;
}

.journal-item__number {
  font-weight: 700;
}

.journal-item__attach {
  color: $zs-ok-fg;
  font-weight: 700;
}

.journal-item__detach {
  color: $zs-remove-fg;
  font-weight: 700;
}

.journal-item__muted {
  color: $zs-text-muted;
}

.journal-item__overdue {
  color: $zs-error-fg;
  font-weight: 600;
}

.journal-item__menu {
  border: 0;
  background: none;
  color: $zs-text-muted;
  cursor: pointer;
  opacity: 0.6;

  &:hover {
    opacity: 1;
  }
}
</style>
