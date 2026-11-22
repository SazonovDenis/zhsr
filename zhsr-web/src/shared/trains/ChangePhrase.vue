<template>
  <i18n-t keypath="{kind} вагона {number}" tag="span" scope="global">
    <template #kind><ChangeKindWord :attach="attach" :text="kind" /></template>
    <template #number
      ><span class="zs-mono change-phrase__number">{{ change.wagon.number }}</span></template
    >
  </i18n-t>
</template>

<script setup lang="ts">
import { computed } from 'vue'

import type { TrainChange } from '@/api/trains'
import { t } from '@/boot/i18n'
import ChangeKindWord from './ChangeKindWord.vue'
import { isAttach } from './trainJournal'

/**
 * «Прицепка вагона 27174» без позиции: ключ «{kind} вагона {number}» переводится целиком, вид — цветом вида
 * (ChangeKindWord) в форме для фразы — ключ «Прицепка (вид во фразе)»: в казахском это другая форма, чем у кнопки
 * «Прицепка»; номер — жирным моноширинным. Текстом без разметки — те же ключи через t() со строками в подстановках.
 */
const props = defineProps<{ change: TrainChange }>()
const attach = computed(() => isAttach(props.change))
const kind = computed(() => (attach.value ? t('Прицепка (вид во фразе)') : t('Отцепка (вид во фразе)')))
</script>

<style scoped lang="scss">
.change-phrase__number {
  font-weight: 700;
}
</style>
