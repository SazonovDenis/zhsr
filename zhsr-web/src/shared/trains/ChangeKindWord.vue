<template>
  <span :class="attach ? 'change-kind-word--attach' : 'change-kind-word--detach'">{{
    text || (attach ? t('Прицепка') : t('Отцепка'))
  }}</span>
</template>

<script setup lang="ts">
import { t } from '@/boot/i18n'

/**
 * Слово вида записи журнала поезда цветом вида: прицепка — зелёным, отцепка — красным; номер вагона и остальной текст
 * рядом — обычным цветом. text — своё слово, уже переведённое (например, «прицеплен»); пусто — «Прицепка» или
 * «Отцепка».
 */
withDefaults(defineProps<{ attach: boolean; text?: string }>(), { text: '' })
</script>

<style scoped lang="scss">
.change-kind-word--attach {
  color: $zs-ok-fg;
}

.change-kind-word--detach {
  color: $zs-remove-fg;
}
</style>
