<template>
  <div class="pwa">
    <header class="pwa__header q-py-xs q-px-sm">
      <!-- логотип для тёмной темы (эталон shared/icons/logo-dark.svg); меню у него нет: язык, пароль и выход — в профиле -->
      <span class="pwa__logo">
        <SvgIcon name="logo-dark" size="1.6rem" />
      </span>
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
        <q-btn
          unelevated
          outline
          no-caps
          color="ink"
          class="pwa__sheet-btn zs-pwa-btn zs-soft-border"
          :label="t('Закрыть')"
          @click="unsentOpen = false"
        />
        <q-btn
          unelevated
          no-caps
          color="primary"
          class="pwa__sheet-btn zs-pwa-btn"
          :label="t('Просмотреть неотправленные')"
          @click="viewUnsent"
        />
      </template>
    </BottomSheet>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { t } from '@/boot/i18n'
import SvgIcon from '@/shared/icons/SvgIcon.vue'
import { bus } from '@/shared/bus'
import { notifyError } from '@/shared/errors'
import { dictManager } from '@/shared/dict/DictManager'
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

// общий статус в шапке: связь и очередь; сбой связи или сервера показывается здесь, а не у записей
const netText = computed(() => {
  const n = outbox.value.length
  if (n === 0) return isOnline.value ? t('В сети') : t('Нет связи')
  if (!isOnline.value) return t('Нет связи, не отправлено: {n}', { n })
  return serverFailed.value ? t('Сервер недоступен, не отправлено: {n}', { n }) : t('В сети, не отправлено: {n}', { n })
})

// «Выход» в профиле доступен всегда: есть неотправленные записи — он покажет их; нет связи — logoutUser скажет об этом
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
// «Выход» в профиле — тот же выход, что в меню шапки
function logoutFromProfile() {
  void onLogout()
}
bus.on('pwa-logout', logoutFromProfile)
onBeforeUnmount(() => {
  bus.off('connection-restored', saveWagons)
  bus.off('pwa-logout', logoutFromProfile)
})

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

// логотип у левого края: отступ слева (0.5rem шапки + 0.2rem блока логотипа + 0.1rem — знак уже своего квадрата 1.6rem) равен
// отступам сверху и снизу (0.25rem шапки + (2.7rem - 1.6rem) / 2)
.pwa__logo {
  display: flex;
  align-items: center;
  height: 2.7rem;
  padding-right: 0.2rem;
  padding-left: 0.2rem;
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

// кнопки — q-btn размера zs-pwa-btn; здесь только раскладка в ряду
.pwa__sheet-btn {
  flex: 1;
}

.pwa__main {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
}
</style>
