<template>
  <q-page class="password-page q-pa-md" :class="{ 'password-page--mobile': isMobile }">
    <form class="password-page__card q-pa-lg" @submit.prevent="submit">
      <div class="password-page__title">{{ t('Смена пароля') }}</div>
      <div class="password-page__hint">
        {{ t('Пароль задан администратором — задайте свой. Пароль — не короче 6 символов.') }}
      </div>

      <FormField label="Текущий пароль">
        <TextInput v-model="oldPassword" password />
      </FormField>
      <FormField label="Новый пароль">
        <TextInput v-model="newPassword" password />
      </FormField>
      <FormField label="Новый пароль ещё раз">
        <TextInput v-model="repeatPassword" password />
      </FormField>

      <ErrorBox v-if="error" :error="error" class="password-page__error q-py-sm q-px-md" />

      <q-btn
        type="submit"
        unelevated
        no-caps
        color="primary"
        class="password-page__submit"
        :label="t('Сменить пароль')"
        :loading="running"
      />
      <button type="button" class="password-page__logout" @click="exit">{{ t('Выйти') }}</button>
    </form>
  </q-page>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'

import { t } from '@/boot/i18n'
import ErrorBox from '@/shared/components/ErrorBox.vue'
import FormField from '@/shared/components/form/FormField.vue'
import TextInput from '@/shared/components/form/TextInput.vue'
import { isMobile } from '@/shared/device'
import { logoutUser } from '@/shared/user'
import { usePasswordChange } from './usePasswordChange'

/**
 * Смена пароля: сюда переводит ответ сервера 406 — пароль задан администратором или пароль по умолчанию, и пока он не
 * сменён, сервер разрешает только смену пароля. После смены — главная страница; «Выйти» — на страницу входа.
 */

const router = useRouter()

const { oldPassword, newPassword, repeatPassword, running, error, save } = usePasswordChange()

async function submit() {
  if (await save()) await router.push({ name: 'home' })
}

async function exit() {
  if (await logoutUser()) await router.push({ name: 'login' })
}
</script>

<style scoped lang="scss">
// как страница входа: форма в верхней трети окна, на телефоне — прижата к верху без рамки
.password-page {
  display: flex;
  flex-direction: column;
  align-items: center;
  background: $zs-bg-header;

  &::before {
    content: '';
    flex: 1;
  }

  &::after {
    content: '';
    flex: 2;
  }

  &--mobile {
    padding: 0;
    background: #fff;

    &::before {
      display: none;
    }
  }
}

.password-page--mobile .password-page__card {
  width: 100%;
  border: 0;
  border-radius: 0;
  box-shadow: none;
}

.password-page__card {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  width: 22rem;
  max-width: 100%;
  border: 0.0625rem solid $zs-border;
  border-radius: 0.3rem;
  background: #fff;
  box-shadow: 0 0.8rem 1.9rem rgba(0, 0, 0, 0.08);
}

.password-page__title {
  color: $zs-menu-bg;
  font-size: 1.3rem;
  font-weight: 700;
}

.password-page__hint {
  color: $zs-text-muted;
  font-size: 0.8rem;
}

.password-page__error {
  border-radius: 0.3rem;
  background: $zs-error-bg;
  color: $zs-error-fg;
  font-size: 0.8rem;
}

.password-page__submit {
  min-height: var(--zs-control-height);
  font-weight: 600;
}

.password-page__logout {
  align-self: center;
  border: 0;
  background: none;
  color: $zs-text-muted;
  font: inherit;
  font-size: 0.8rem;
  cursor: pointer;

  &:hover {
    text-decoration: underline;
  }
}
</style>
