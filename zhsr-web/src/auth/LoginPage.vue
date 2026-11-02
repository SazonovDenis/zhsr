<template>
  <q-page class="login-page" :class="{ 'login-page--mobile': isMobile }">
    <form ref="formEl" class="login-page__card" @submit.prevent="submit">
      <!-- выбор языка — в рамке формы, справа от названия -->
      <div class="login-page__brand">
        <SvgIcon name="logo" size="2.1rem" />
        <span class="login-page__title">{{ t('Жолсерік') }}</span>
        <LanguageSwitcher
          class="login-page__lang"
          icon-size="1.3rem"
          menu-anchor="bottom right"
          menu-self="top right"
        />
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
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

import { login } from '@/api/session'
import { t } from '@/boot/i18n'
import FormField from '@/shared/components/form/FormField.vue'
import TextInput from '@/shared/components/form/TextInput.vue'
import LanguageSwitcher from '@/shared/components/LanguageSwitcher.vue'
import { isMobile } from '@/shared/device'
import SvgIcon from '@/shared/icons/SvgIcon.vue'

/**
 * Заготовка страницы входа: логин, пароль и «Войти». После входа — главная страница; часть приложения (мобильная или
 * десктопная) выбирается по устройству (см. src/router/index.ts). Язык можно сменить до входа — переключатель в рамке
 * формы. На десктопе форма — карточкой в верхней трети окна; на телефоне — прижата к верху, без фона страницы и рамки.
 */

const router = useRouter()

const username = ref('')
const password = ref('')
const running = ref(false)
const error = ref('')

// при открытии страницы фокус — в поле логина
const formEl = ref<HTMLFormElement | null>(null)
onMounted(() => formEl.value?.querySelector('input')?.focus())

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
// форма — в верхней трети окна: свободное место над ней и под ней делится как 1 : 2
.login-page {
  position: relative;
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

  // телефон: форма прижата к верху, без фона страницы и без рамки карточки
  &--mobile {
    padding: 0;
    background: #fff;

    &::before {
      display: none;
    }
  }
}

.login-page--mobile .login-page__card {
  width: 100%;
  border: 0;
  border-radius: 0;
  box-shadow: none;
}

// выбор языка в строке с названием — крупнее обычного (иконка 1.3rem, код языка 0.9rem) и не сжимается, в обоих
// вариантах
.login-page__lang {
  flex: none;
  margin-left: auto;
  color: $zs-text-muted;

  :deep(.language-switcher__short) {
    font-size: 0.9rem;
  }
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
