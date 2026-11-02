<template>
  <PwaPage>
    <div v-if="user" class="today__hello">{{ t('Здравствуйте, {name}!', { name: user.firstName }) }}</div>

    <!-- текущий рейс пользователя -->
    <div v-if="trip" class="today__trip">
      <div class="today__trip-head">
        <span class="today__trip-train">{{ t('Рейс {train}', { train: trip.train }) }}</span>
        <DictBadge :id="trip.state" dict="TripState" />
      </div>
      <div class="today__trip-name">{{ trip.name }}</div>
      <div class="today__tiles">
        <div v-if="trip.duty === 'CONDUCTOR' && trip.wagons.length > 0" class="today__tile">
          <span class="today__tile-key">{{ t('Мой вагон') }}</span>
          <span class="today__tile-value zs-mono">{{ trip.wagons.join(', ') }}</span>
        </div>
        <div v-else class="today__tile">
          <span class="today__tile-key">{{ t('Отправление') }}</span>
          <span class="today__tile-value">{{ formatDateTime(trip.departure) }}</span>
        </div>
        <div class="today__tile">
          <span class="today__tile-key">{{ t('Прибытие') }}</span>
          <span class="today__tile-value">{{ formatDateTime(trip.arrival) }}</span>
        </div>
      </div>
      <!-- начальник поезда распределяет проводников по вагонам -->
      <router-link v-if="trip.duty === 'LNP'" :to="{ name: 'pwa-trip' }" class="today__crew">
        <span>{{ t('Формирование • проводники') }}</span>
        <span v-if="norm" class="today__norm" :class="{ 'today__norm--ok': normDone(norm) }">
          {{ t('{have} из {need}', { have: norm.have, need: norm.need }) }}
        </span>
      </router-link>
    </div>

    <div v-if="stale" class="today__stale">{{ t('Нет связи — показаны сохранённые данные') }}</div>
    <div v-if="error" class="today__error">{{ error }}</div>

    <RequestSection
      v-for="s in sections"
      :key="s.key"
      :title="t(s.title)"
      :requests="s.requests"
      :queued="s.queued"
      :empty="t(s.empty)"
    />
  </PwaPage>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, shallowRef } from 'vue'

import type { RepairRequest } from '@/api/requests'
import { fetchRequests, requestFilter } from '@/api/requests'
import type { TripCrew } from '@/api/trips'
import { fetchTripCrew } from '@/api/trips'
import { t } from '@/boot/i18n'
import { bus } from '@/shared/bus'
import DictBadge from '@/shared/components/DictBadge.vue'
import { formatDateTime } from '@/shared/format'
import { loadCached } from '@/shared/offline/cache'
import { outbox, outboxAsRequest } from '@/shared/offline/outbox'
import { canAccept, canWork } from '@/shared/requests/rights'
import { currentUser, hasRole, loadCurrentUser } from '@/shared/user'
import PwaPage from '../components/PwaPage.vue'
import RequestSection from '../components/RequestSection.vue'
import { crewNorm, normDone } from '../trip/crewNorm'

/**
 * Главный экран мобильной части «Сегодня»: текущий рейс пользователя и разделы заявок по его ролям. Роли совмещаются,
 * поэтому разделы набираются по всем ролям пользователя:
 * - проводник и начальник поезда — «Ждут вашей приёмки» и «Ваши открытые заявки»; у начальника поезда в карточке
 *   рейса — переход к формированию проводников;
 * - сотрудник службы (ПЭМ, мастер депо, клининг, экипировщик) — «На исполнение» и «Ждут приёмки автора»;
 * - инструктор поездных бригад и эксплуатационщик — «Ждут вашей приёмки».
 * Без связи показываются сохранённые на телефоне данные.
 */

const user = computed(() => currentUser.value)
const trip = computed(() => user.value?.trip ?? null)

const requests = shallowRef<RepairRequest[]>([])
const stale = ref(false)
const error = ref('')
const crew = shallowRef<TripCrew | null>(null)
const norm = computed(() => (crew.value ? crewNorm(crew.value) : null))

const OPEN_WORK = ['NEW', 'ASSIGNED', 'IN_PROGRESS', 'DEFERRED']

interface Section {
  key: string
  title: string
  empty: string
  requests: RepairRequest[]
  queued: RepairRequest[]
}

const sections = computed<Section[]>(() => {
  const u = user.value
  if (!u) return []
  const list = requests.value
  const result: Section[] = []
  const add = (s: Section) => {
    if (!result.some((x) => x.key === s.key)) result.push(s)
  }
  const accept = () =>
    add({
      key: 'accept',
      title: 'Ждут вашей приёмки',
      empty: 'Нет работ на приёмку',
      requests: list.filter((r) => r.status === 'DONE' && canAccept(u, r)),
      queued: [],
    })

  if (hasRole(u, 'CONDUCTOR') || hasRole(u, 'LNP')) {
    accept()
    add({
      key: 'mine',
      title: 'Ваши открытые заявки',
      empty: 'Открытых заявок нет',
      requests: list.filter((r) => r.author.id === u.id && r.status !== 'DONE'),
      queued: outbox.value.filter((x) => x.author === u.id).map((x) => outboxAsRequest(x, u.name)),
    })
  }
  if (u.services.some((s) => ['TECHNICAL', 'CLEANING', 'EQUIPMENT'].includes(s))) {
    add({
      key: 'todo',
      title: 'На исполнение',
      empty: 'Всё сделано',
      requests: list.filter((r) => OPEN_WORK.includes(r.status) && (r.executor?.id === u.id || canWork(u, r))),
      queued: [],
    })
    add({
      key: 'waiting',
      title: 'Ждут приёмки автора',
      empty: 'Нет работ на приёмку',
      requests: list.filter((r) => r.status === 'DONE' && r.executor?.id === u.id),
      queued: [],
    })
  }
  if (hasRole(u, 'INSTRUCTOR') || hasRole(u, 'OPERATOR') || hasRole(u, 'ADMIN')) accept()
  return result
})

async function load() {
  error.value = ''
  try {
    const u = await loadCurrentUser()
    const result = await loadCached('today', () => fetchRequests(requestFilter('all', { closed: false })))
    requests.value = result.data
    stale.value = result.stale
    if (u.trip?.duty === 'LNP') {
      const id = u.trip.id
      crew.value = (await loadCached(`crew:${id}`, () => fetchTripCrew(id))).data
    }
  } catch (e) {
    error.value = e instanceof Error ? t(e.message) : String(e)
  }
}

const onRefresh = () => void load()
bus.on('requests-refresh', onRefresh)
bus.on('connection-restored', onRefresh)
onBeforeUnmount(() => {
  bus.off('requests-refresh', onRefresh)
  bus.off('connection-restored', onRefresh)
})

onMounted(load)
</script>

<style scoped lang="scss">
.today__hello {
  font-size: 1.375rem;
  font-weight: 700;
}

.today__trip {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  padding: 1rem;
  border: 0.0625rem solid $zs-border;
  border-radius: 0.375rem;
  background: #fff;
}

.today__trip-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
}

.today__trip-train {
  color: $zs-text-muted;
  font-size: 0.75rem;
  font-weight: 700;
  letter-spacing: 0.06em;
  text-transform: uppercase;
}

.today__trip-name {
  font-size: 1.0625rem;
  font-weight: 600;
  line-height: 1.3;
}

.today__tiles {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0.625rem;
}

.today__tile {
  display: flex;
  flex-direction: column;
  gap: 0.125rem;
  padding: 0.625rem 0.75rem;
  border-radius: 0.25rem;
  background: $zs-row-selected-bg;
}

.today__tile-key {
  color: $zs-text-muted;
  font-size: 0.75rem;
}

.today__tile-value {
  font-size: 1.0625rem;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

.today__crew {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
  padding-top: 0.625rem;
  border-top: 0.0625rem solid $zs-border;
  color: $primary;
  font-size: 0.9375rem;
  font-weight: 600;
  text-decoration: none;
}

.today__norm {
  flex: none;
  padding: 0.125rem 0.5rem;
  border-radius: 62.5rem;
  background: #fbe9cf;
  color: #8a4b05;
  font-size: 0.8125rem;

  &--ok {
    background: #dff0e2;
    color: #1d5e2c;
  }
}

.today__stale {
  color: #7a5200;
  font-size: 0.8125rem;
}

.today__error {
  padding: 0.625rem 0.875rem;
  border-radius: 0.25rem;
  background: #fbe5e1;
  color: #8f2416;
  font-size: 0.8125rem;
}
</style>
