<template>
  <!-- ФИО и плашка «вы» не переносятся: строка целиком (white-space: nowrap) -->
  <span class="user-name"
    >{{ user.name
    }}<template v-if="isMe(user)"
      >&nbsp;<InlineBadge>{{ t('вы') }}</InlineBadge></template
    ></span
  >
</template>

<script setup lang="ts">
import { t } from '@/boot/i18n'
import { isMe } from '@/shared/user'
import InlineBadge from './InlineBadge.vue'

/**
 * ФИО сотрудника в разметке: текущий пользователь — с серой инлайн-плашкой «вы» (InlineBadge). Обычным текстом
 * (подсказки, текст поля) — userName с пометкой «(вы)».
 */
defineProps<{ user: { id: number; name: string } }>()
</script>

<style scoped lang="scss">
// ФИО не разрывается переносом
.user-name {
  white-space: nowrap;
}
</style>
