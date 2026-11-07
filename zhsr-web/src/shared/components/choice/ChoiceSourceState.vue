<template>
  <q-spinner
    v-if="source.loading"
    color="primary"
    size="1.3rem"
    class="choice-source-state__spinner q-my-none q-mx-sm"
  />
  <button
    v-else-if="source.error"
    type="button"
    class="choice-source-state__error zs-focusable q-pa-none"
    :title="t('Загрузить заново')"
    @click="source.retry()"
  >
    <ErrorBox :error="source.error" />
  </button>
</template>

<script setup lang="ts">
import { t } from '@/boot/i18n'
import ErrorBox from '@/shared/components/ErrorBox.vue'
import type { Loadable } from '@/shared/loadable'

/**
 * Загрузка вариантов ряда кнопок (CbButton, CbSegment) на месте кнопок: спиннер, пока варианты грузятся, и ошибка, если
 * загрузка упала. Клик по ошибке — загрузить заново. Варианты есть и не грузятся — ничего не показывает.
 */
defineProps<{ source: Loadable }>()
</script>

<style scoped lang="scss">
.choice-source-state__error {
  border: 0;
  background: none;
  font: inherit;
  text-align: left;
  cursor: pointer;
}
</style>
