<template>
  <q-page class="flex flex-center">
    <div class="text-h4">{{ t('Привет, мир! Мобильная версия') }}</div>
  </q-page>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
</script>
