<template>
  <template v-if="typeof phrase === 'string'">{{ phrase }}</template>
  <b v-else-if="'bold' in phrase">{{ phrase.bold }}</b>
  <!-- щелчок по ссылке не выбирает строку списка, в которой стоит фраза -->
  <span v-else-if="'trip' in phrase" @click.stop.prevent @dblclick.stop>
    <SoftLink class="phrase-view__link" text="{n}" :params="{ n: phrase.text }" @click="open(phrase)" />
  </span>
  <i18n-t v-else :keypath="phrase.key" tag="span" scope="global">
    <template v-for="(part, name) in phrase.params" :key="name" #[name]>
      <PhraseView :phrase="part" />
    </template>
  </i18n-t>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'

import SoftLink from '@/shared/components/form/SoftLink.vue'
import { isMobile } from '@/shared/device'
import type { Phrase } from './phrase'

/**
 * Фраза (Phrase) разметкой: жирные части и рейс мягкой ссылкой. Ссылка открывает рейс в новой вкладке, чтобы окно с
 * введёнными данными осталось: на десктопе — состав поезда рейса, на телефоне — вагоны рейса.
 */
defineProps<{ phrase: Phrase }>()

const router = useRouter()

function open(link: { trip: number; train: number }) {
  const to = isMobile.value
    ? { name: 'pwa-trip-wagons', params: { id: link.trip } }
    : { name: 'train-form', params: { id: link.train }, query: { trip: String(link.trip) } }
  window.open(router.resolve(to).href, '_blank')
}
</script>

<style scoped lang="scss">
// ссылка внутри фразы — размером как фраза
.soft-link.phrase-view__link {
  font-size: inherit;
}
</style>
