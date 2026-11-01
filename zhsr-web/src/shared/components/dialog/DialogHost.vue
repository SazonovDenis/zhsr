<template>
  <component
    :is="dialog.component"
    v-for="dialog in openedDialogs"
    :key="dialog.id"
    v-bind="dialog.props"
    :show="dialog.show"
    @update:show="(show: boolean) => !show && dialog.finish(undefined)"
    @done="(payload: unknown) => dialog.finish(payload)"
  />
</template>

<script setup lang="ts">
import { openedDialogs } from './openDialog'

/** Место для диалогов, открытых через openDialog. Подключается один раз в App.vue. */
</script>
