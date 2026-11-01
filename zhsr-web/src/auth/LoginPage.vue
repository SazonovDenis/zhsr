<template>
  <q-page class="login-page">
    <div class="login-page__lang">
      <LanguageSwitcher menu-anchor="bottom left" menu-self="top left" />
    </div>

    <form class="login-page__card" @submit.prevent="submit">
      <div class="login-page__brand">
        <SvgIcon name="logo" size="2.5rem" />
        <span class="login-page__title">{{ t('Жолсерік') }}</span>
      </div>

      <FormField label="Логин">
        <TextInput v-model="username" />
      </FormField>
      <FormField label="Пароль">
        <TextInput v-model="password" password />
      </FormField>

      <div v-if="error" class="login-page__error">{{ error }}</div>

      <q-btn
        type="submit"
        unelevated
        no-caps
        color="primary"
        class="login-page__submit"
        :label="t('Войти')"
        :loading="running"
      />
    </form>
  </q-page>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'

import { login } from '@/api/session'
import { t } from '@/boot/i18n'
import FormField from '@/shared/components/form/FormField.vue'
import TextInput from '@/shared/components/form/TextInput.vue'
import LanguageSwitcher from '@/shared/components/LanguageSwitcher.vue'
import SvgIcon from '@/shared/icons/SvgIcon.vue'

/**
 * Заготовка страницы входа: логин, пароль и «Войти». После входа — главная страница; часть приложения (мобильная или
 * десктопная) выбирается по устройству (см. src/router/index.ts). Язык можно сменить до входа.
 */

const router = useRouter()

const username = ref('')
const password = ref('')
const running = ref(false)
const error = ref('')

async function submit() {
  error.value = ''
  running.value = true
  try {
    await login(username.value, password.value)
    await router.push({ name: 'home' })
  } catch (e) {
    error.value = t(e instanceof Error ? e.message : String(e))
  } finally {
    running.value = false
  }
}
</script>

<style scoped lang="scss">
.login-page {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 1rem;
  background: $zs-bg-header;
}

.login-page__lang {
  position: absolute;
  top: 1rem;
  right: 1rem;
  color: $zs-text-muted;
}

.login-page__card {
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

.login-page__brand {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  margin-bottom: 0.5rem;
}

.login-page__title {
  color: $zs-menu-bg;
  font-size: 1.5rem;
  font-weight: 700;
  letter-spacing: -0.01em;
}

.login-page__error {
  padding: 0.625rem 0.875rem;
  border-radius: 0.25rem;
  background: #fbe5e1;
  color: #8f2416;
  font-size: 0.8125rem;
}

.login-page__submit {
  min-height: var(--zs-control-height);
  font-weight: 600;
}
</style>
