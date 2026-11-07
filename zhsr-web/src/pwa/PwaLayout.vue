<template>
  <div class="pwa">
    <header class="pwa__header q-py-xs q-px-sm">
      <button type="button" class="pwa__burger q-pa-none" :title="t('Меню')">
        <q-icon name="menu" size="1.5rem" />
        <q-menu anchor="bottom left" self="top left" :offset="[0, 6]">
          <q-list class="pwa__menu">
            <q-item
              v-for="option in LOCALES"
              :key="option.value"
              v-close-popup
              clickable
              :active="option.value === locale"
              active-class="pwa__menu-active"
              @click="setLocale(option.value)"
            >
              <q-item-section side class="pwa__menu-side pwa__menu-code q-pr-none">{{ option.short }}</q-item-section>
              <q-item-section>{{ option.label }}</q-item-section>
            </q-item>
            <q-separator />
            <q-item v-close-popup clickable @click="openDialog(ChangePasswordDialog)">
              <q-item-section side class="pwa__menu-side q-pr-none"><q-icon name="lock" /></q-item-section>
              <q-item-section>{{ t('Сменить пароль') }}</q-item-section>
            </q-item>
            <q-separator />
            <q-item v-close-popup clickable @click="onLogout">
              <q-item-section side class="pwa__menu-side q-pr-none"><q-icon name="logout" /></q-item-section>
              <q-item-section>{{ t('Выход') }}</q-item-section>
            </q-item>
          </q-list>
        </q-menu>
      </button>
      <span class="pwa__brand">{{ t(route.meta.title ?? 'Жолсерік') }}</span>
      <span class="pwa__net" :class="{ 'pwa__net--off': !isOnline }">
        <span class="pwa__net-dot" />
        {{ netText }}
      </span>
    </header>

    <main class="pwa__main">
      <router-view />
    </main>

    <PwaTabs v-if="route.meta.tabs" />

    <BottomSheet v-model:show="unsentOpen" :title="t('Есть неотправленные записи')">
      <div>{{ t('Выйти нельзя, пока на телефоне есть неотправленные записи.') }}</div>
      <template #buttons>
        <button type="button" class="pwa__sheet-btn q-py-xs q-px-md" @click="unsentOpen = false">
          {{ t('Закрыть') }}
        </button>
        <button type="button" class="pwa__sheet-btn pwa__sheet-btn--primary q-py-xs q-px-md" @click="viewUnsent">
          {{ t('Просмотреть неотправленные') }}
        </button>
      </template>
    </BottomSheet>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import ChangePasswordDialog from '@/auth/ChangePasswordDialog.vue'
import { i18n, t } from '@/boot/i18n'
import { bus } from '@/shared/bus'
import { openDialog } from '@/shared/components/dialog/openDialog'
import { notifyError } from '@/shared/errors'
import { dictManager } from '@/shared/dict/DictManager'
import { LOCALES, setLocale } from '@/shared/locale'
import { isOnline } from '@/shared/network'
import { outbox, serverFailed, startOutbox } from '@/shared/offline/outbox'
import { REQUEST_DICTS } from '@/shared/requests/requestView'
import { saveTripWagons } from '@/shared/requests/useWagonPick'
import { currentUser, loadCurrentUser, logoutUser } from '@/shared/user'
import BottomSheet from './components/BottomSheet.vue'
import PwaTabs from './components/PwaTabs.vue'

/**
 * Каркас мобильной части: шапка (меню слева — языки, смена пароля и выход, название, есть ли связь и сколько записей не
 * отправлено),
 * страница и нижняя панель вкладок — у страниц с meta.tabs. Страницы, в которые «проваливаются» (карточка заявки,
 * мастер подачи, формирование рейса), рисуют свою шапку с кнопкой «назад» и свою нижнюю панель (PwaPage).
 *
 * При старте загружаются текущий пользователь и справочники заявок, читается очередь неотправленных заявок; без
 * связи всё это берётся из копии на телефоне. Состав текущего рейса сохраняется на телефоне заранее: без связи он нужен
 * для выбора вагона в заявке.
 */

const route = useRoute()
const router = useRouter()

const locale = computed(() => i18n.global.locale.value)

// общий статус в шапке: связь и очередь; сбой связи или сервера показывается здесь, а не у записей
const netText = computed(() => {
  const n = outbox.value.length
  if (n === 0) return isOnline.value ? t('В сети') : t('Нет связи')
  if (!isOnline.value) return t('Нет связи, не отправлено: {n}', { n })
  return serverFailed.value ? t('Сервер недоступен, не отправлено: {n}', { n }) : t('В сети, не отправлено: {n}', { n })
})

// пункт «Выход» доступен всегда: есть неотправленные записи — он покажет их; нет связи — logoutUser скажет об этом
const unsentOpen = ref(false)

function viewUnsent() {
  unsentOpen.value = false
  void router.push({ name: 'pwa-requests', query: { filter: 'unsent' } })
}

async function onLogout() {
  if (outbox.value.length > 0) {
    unsentOpen.value = true
    return
  }
  if (await logoutUser()) await router.push({ name: 'login' })
}

// состав рейса — на телефон: при входе, смене текущего рейса и возврате связи; это независимая загрузка, ошибка — всплывашкой
const tripId = computed(() => currentUser.value?.trip?.id ?? null)
function saveWagons() {
  if (tripId.value !== null) saveTripWagons(tripId.value).catch(notifyError)
}
watch(tripId, saveWagons)
bus.on('connection-restored', saveWagons)
onBeforeUnmount(() => bus.off('connection-restored', saveWagons))

onMounted(async () => {
  saveWagons()
  await Promise.all([
    loadCurrentUser().catch(notifyError),
    dictManager.prepareDicts(...REQUEST_DICTS, 'TripState', 'Role'),
    startOutbox(),
  ])
})
</script>

<style scoped lang="scss">
// во всю видимую высоту экрана телефона: 100dvh не учитывает адресную строку
.pwa {
  display: flex;
  flex-direction: column;
  height: 100dvh;
  background: $zs-bg-header;
  color: $zs-text;
  font-size: 0.9rem;
}

.pwa__header {
  display: flex;
  flex: none;
  align-items: center;
  gap: 0.5rem;
  background: var(--zs-header-bg);
  color: #fff;
}

.pwa__burger {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 2.7rem;
  height: 2.7rem;
  border: 0;
  border-radius: 0.4rem;
  background: transparent;
  color: inherit;
  cursor: pointer;

  &:active {
    background: rgba(255, 255, 255, 0.12);
  }
}

.pwa__brand {
  flex: 1;
  font-size: 1.1rem;
  font-weight: 700;
  letter-spacing: 0;
}

.pwa__net {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  color: $zs-menu-fg;
  font-size: 0.8rem;
  text-align: right;

  &--off {
    color: #f7b733;
    font-weight: 700;
  }
}

.pwa__net-dot {
  width: 0.5rem;
  height: 0.5rem;
  border-radius: 50%;
  background: #3fbf6b;

  .pwa__net--off & {
    background: #f2a516;
  }
}

.pwa__menu {
  min-width: 12rem;
}

// левая колонка меню одной ширины: текст всех пунктов начинается с одного места
.pwa__menu-side {
  width: 2.6rem;
  min-width: 2.6rem;
}

.pwa__menu-code {
  color: $zs-text-muted;
  font-weight: 600;
}

.pwa__menu-active {
  background: $zs-selected-bg;
  color: $zs-selected-fg;
}

.pwa__sheet-btn {
  flex: 1;
  min-height: 2.8rem;
  border: 0.0625rem solid $zs-border;
  border-radius: 0.4rem;
  background: #fff;
  color: $zs-text;
  font: inherit;
  cursor: pointer;

  &--primary {
    border-color: $primary;
    background: $primary;
    color: #fff;
  }
}

.pwa__main {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
}
</style>
