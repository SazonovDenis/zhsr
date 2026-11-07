<template>
  <div class="load-spinner q-py-lg q-px-md">
    <q-spinner color="primary" :size="size" />
  </div>
</template>

<script setup lang="ts">
/** Круглый спиннер по центру места, где ждут загрузку: список, карточка. */
withDefaults(defineProps<{ size?: string }>(), { size: '2.5rem' })
</script>

<style scoped lang="scss">
.load-spinner {
  display: flex;
  align-items: center;
  justify-content: center;
}
</style>
