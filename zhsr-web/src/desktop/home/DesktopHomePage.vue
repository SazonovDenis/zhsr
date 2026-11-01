<template>
  <div class="flex flex-center full-height">
    <div class="text-h4">{{ t('Привет, мир! Пользовательская часть') }}</div>
  </div>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
</script>
