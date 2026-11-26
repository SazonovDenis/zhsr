<template>
  <div class="row no-wrap">
    <q-btn
      flat
      dense
      round
      size="sm"
      color="primary"
      icon="arrow_upward"
      :disable="!move.canMove(row.wagon, -1)"
      :title="t('Вверх')"
      :aria-label="t('Вверх')"
      @click.stop="move.move(row.wagon, -1)"
    />
    <q-btn
      flat
      dense
      round
      size="sm"
      color="primary"
      icon="arrow_downward"
      :disable="!move.canMove(row.wagon, 1)"
      :title="t('Вниз')"
      :aria-label="t('Вниз')"
      @click.stop="move.move(row.wagon, 1)"
    />
  </div>
</template>

<script setup lang="ts">
import { inject } from 'vue'

import { t } from '@/boot/i18n'
import type { CellProps } from '@/shared/components/table/types'
import { TRAIN_WAGON_SEQ_MOVE } from '../trainWagonSeqMove'

/** Кнопки «Вверх» и «Вниз» строки окна «Сменить порядок»: у крайних строк кнопка наружу неактивна. */
defineProps<CellProps<{ wagon: number }>>()

const move = inject(TRAIN_WAGON_SEQ_MOVE)!
</script>
