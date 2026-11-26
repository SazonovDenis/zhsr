<template>
  <span v-if="!change.wagon">{{ t('Смена порядка вагонов') }}</span>
  <i18n-t v-else keypath="{kind} вагона {number}" tag="span" scope="global">
    <template #kind>{{ kind }}</template>
    <template #number
      ><span class="zs-mono change-phrase__number">{{ change.wagon.number }}</span></template
    >
  </i18n-t>
</template>

<script setup lang="ts">
import { computed } from 'vue'

import type { TrainChange } from '@/api/trains'
import { t } from '@/boot/i18n'
import { isAttach } from './trainJournal'

/**
 * Смена порядка — «Смена порядка вагонов». «Прицепка вагона 27174» без позиции: ключ «{kind} вагона {number}» переводится целиком, вид — в форме для фразы — ключ «Прицепка (вид во фразе)»: в казахском это другая форма, чем у кнопки
 * «Прицепка»; номер — жирным моноширинным. Текстом без разметки — те же ключи через t() со строками в подстановках.
 */
const props = defineProps<{ change: TrainChange }>()
const kind = computed(() => (isAttach(props.change) ? t('Прицепка (вид во фразе)') : t('Отцепка (вид во фразе)')))
</script>

<style scoped lang="scss">
.change-phrase__number {
  font-weight: 700;
}
</style>
