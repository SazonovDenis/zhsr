<template>
  <SoftLink :text="text" :params="params" @click="go" />
</template>

<script setup lang="ts">
import type { RouteLocationRaw } from 'vue-router'
import { useRouter } from 'vue-router'

import SoftLink from './SoftLink.vue'

/**
 * Мягкая ссылка-переход (SoftLink): по нажатию — на страницу to, например в список с готовым фильтром. Текст — на
 * русском, переводится при показе.
 *
 *   <SoftRouteLink text="Рейсы вагона" :to="{ name: 'trips', query: { wagon: number } }" />
 */
const props = defineProps<{
  text: string
  to: RouteLocationRaw
  /** значения для подстановки в текст */
  params?: Record<string, unknown>
}>()

const router = useRouter()

function go() {
  void router.push(props.to)
}
</script>
