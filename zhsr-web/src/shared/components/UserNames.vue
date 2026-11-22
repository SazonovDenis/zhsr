<template>
  <span v-if="users.length > 0"
    ><template v-for="(u, i) in users" :key="u.id"
      ><UserName :user="u" /><template v-if="i < users.length - 1">, </template></template
    ></span
  >
  <span v-else>{{ t(empty) }}</span>
</template>

<script setup lang="ts">
import { t } from '@/boot/i18n'
import UserName from './UserName.vue'

/**
 * Несколько сотрудников через запятую: каждый — UserName (текущий пользователь — с плашкой «вы»). Пусто — текст
 * empty (переводится).
 */
withDefaults(defineProps<{ users: { id: number; name: string }[]; empty?: string }>(), { empty: 'не назначен' })
</script>
