<template>
  <PwaPage>
    <div v-if="user" class="profile__head">
      <div class="profile__name">{{ user.name }}</div>
      <div class="profile__roles">{{ roles }}</div>
    </div>
    <div class="profile__stub q-pa-md">{{ t('Раздел в разработке. Язык и выход — в меню слева вверху.') }}</div>
  </PwaPage>
</template>

<script setup lang="ts">
import { computed } from 'vue'

import { t } from '@/boot/i18n'
import { dictManager } from '@/shared/dict/DictManager'
import { currentUser } from '@/shared/user'
import PwaPage from '../components/PwaPage.vue'

/** Профиль пользователя — пока заглушка: фамилия и роли. Язык и выход — в меню шапки. */
const user = computed(() => currentUser.value)
const roles = computed(() => user.value?.roles.map((r) => dictManager.getValue('Role', r)).join(', ') ?? '')
</script>

<style scoped lang="scss">
.profile__head {
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
}

.profile__name {
  font-size: 1.4rem;
  font-weight: 700;
}

.profile__roles {
  color: $zs-text-muted;
  font-size: 0.9rem;
}

.profile__stub {
  border: 0.0625rem dashed $zs-border;
  border-radius: 0.4rem;
  color: $zs-text-muted;
  font-size: 0.9rem;
}
</style>
