<template>
  <!-- svg вставляется разметкой, а не картинкой: линии иконки берут цвет текста (currentColor) -->
  <!-- eslint-disable-next-line vue/no-v-html -- содержимое — svg-файлы этой папки, не пользовательский ввод -->
  <span class="svg-icon" :style="{ fontSize: size }" aria-hidden="true" v-html="svg" />
</template>

<script setup lang="ts">
import { computed } from 'vue'

import type { IconName } from './icons'
import { icons } from './icons'

/**
 * Иконка из src/shared/icons. Размер — сторона квадрата в rem или em, цвет — цвет текста родителя.
 */
const props = withDefaults(defineProps<{ name: IconName; size?: string }>(), { size: '1.1rem' })

const svg = computed(() => icons[props.name])
</script>

<style scoped lang="scss">
.svg-icon {
  display: inline-flex;
  flex: none;
  width: 1em;
  height: 1em;
  line-height: 0;

  :deep(svg) {
    width: 100%;
    height: 100%;
  }
}
</style>
