<template>
  <q-page class="password-page" :class="{ 'password-page--mobile': isMobile }">
    <form class="password-page__card" @submit.prevent="submit">
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

      <ErrorBox v-if="error" :error="error" class="password-page__error" />

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
import { ref } from 'vue'
import { useRouter } from 'vue-router'

import { changePassword, logout } from '@/api/session'
import { t } from '@/boot/i18n'
import ErrorBox from '@/shared/components/ErrorBox.vue'
import type { ShownError } from '@/shared/errors'
import { formError, shownError } from '@/shared/errors'
import FormField from '@/shared/components/form/FormField.vue'
import TextInput from '@/shared/components/form/TextInput.vue'
import { isMobile } from '@/shared/device'

/**
 * Смена пароля: сюда переводит ответ сервера 406 — пароль задан администратором или пароль по умолчанию, и пока он не
 * сменён, сервер разрешает только смену пароля. После смены — главная страница; «Выйти» — на страницу входа.
 */

const router = useRouter()

const oldPassword = ref('')
const newPassword = ref('')
const repeatPassword = ref('')
const running = ref(false)
// ошибка формы: проверки (formError) или запроса (shownError, запрос второй строкой); null — ошибки нет
const error = ref<ShownError | null>(null)

async function submit() {
  if (!oldPassword.value || !newPassword.value) {
    error.value = formError(t('Заполните все поля'))
    return
  }
  if (newPassword.value !== repeatPassword.value) {
    error.value = formError(t('Новый пароль и повтор не совпадают'))
    return
  }
  error.value = null
  running.value = true
  try {
    await changePassword(oldPassword.value, newPassword.value)
    await router.push({ name: 'home' })
  } catch (e) {
    error.value = shownError(e)
  } finally {
    running.value = false
  }
}

async function exit() {
  try {
    await logout()
  } finally {
    await router.push({ name: 'login' })
  }
}
</script>

<style scoped lang="scss">
// как страница входа: форма в верхней трети окна, на телефоне — прижата к верху без рамки
.password-page {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 1rem;
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
  padding: 2rem 1.75rem;
  border: 0.0625rem solid $zs-border;
  border-radius: 0.25rem;
  background: #fff;
  box-shadow: 0 0.75rem 1.9rem rgba(0, 0, 0, 0.08);
}

.password-page__title {
  color: $zs-menu-bg;
  font-size: 1.25rem;
  font-weight: 700;
}

.password-page__hint {
  color: $zs-text-muted;
  font-size: 0.8125rem;
}

.password-page__error {
  padding: 0.625rem 0.875rem;
  border-radius: 0.25rem;
  background: $zs-error-bg;
  color: $zs-error-fg;
  font-size: 0.8125rem;
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
  font-size: 0.8125rem;
  cursor: pointer;

  &:hover {
    text-decoration: underline;
  }
}
</style>
