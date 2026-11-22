<template>
  <!-- фраза переводится целиком (порядок слов — у переводчика), цветное слово вида и номер — подстановками -->
  <i18n-t v-if="withSeq" keypath="{phrase} на {n}" tag="span" scope="global">
    <template #phrase><ChangePhrase :change="change" /></template>
    <template #n>{{ change.seq }}</template>
  </i18n-t>
  <ChangePhrase v-else :change="change" />
</template>

<script setup lang="ts">
import { computed } from 'vue'

import type { TrainChange } from '@/api/trains'
import ChangePhrase from './ChangePhrase.vue'
import { isAttach } from './trainJournal'

/**
 * Запись журнала поезда одной фразой, как в журнале: «Прицепка вагона 27174 на 7» или «Отцепка вагона 15030» — вид
 * цветом вида, номер вагона жирным моноширинным, остальное обычным цветом. Фраза переводится целиком: в казахском —
 * «27174 вагонын тіркеу, орын: 7». seq — показывать позицию прицепки.
 */
const props = withDefaults(defineProps<{ change: TrainChange; seq?: boolean }>(), { seq: true })
const withSeq = computed(() => props.seq && isAttach(props.change) && props.change.seq !== null)
</script>
