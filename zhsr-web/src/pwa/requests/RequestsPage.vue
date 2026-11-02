<template>
  <PwaPage>
    <div class="requests__title">{{ t('Заявки') }}</div>

    <div class="requests__pills">
      <button
        v-for="s in scopes"
        :key="s.id"
        type="button"
        class="requests__pill"
        :class="{ 'requests__pill--on': scope === s.id }"
        @click="scope = s.id"
      >
        {{ t(s.text) }}
      </button>
    </div>
    <div class="requests__pills">
      <button
        v-for="c in closedOptions"
        :key="c.text"
        type="button"
        class="requests__pill requests__pill--small"
        :class="{ 'requests__pill--on': closed === c.id }"
        @click="closed = c.id"
      >
        {{ t(c.text) }}
      </button>
    </div>

    <div v-if="stale" class="requests__stale">{{ t('Нет связи — показаны сохранённые данные') }}</div>
    <div v-if="error" class="requests__error">{{ error }}</div>

    <RequestSection
      :title="t(closed ? 'Закрытые' : 'Открытые')"
      :requests="requests"
      :queued="queued"
      :empty="t('Ничего не найдено')"
    />
  </PwaPage>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue'

import type { RepairRequest, RequestScope } from '@/api/requests'
import { fetchRequests, requestFilter } from '@/api/requests'
import { t } from '@/boot/i18n'
import { bus } from '@/shared/bus'
import { loadCached } from '@/shared/offline/cache'
import { outbox, outboxAsRequest } from '@/shared/offline/outbox'
import { currentUser } from '@/shared/user'
import PwaPage from '../components/PwaPage.vue'
import RequestSection from '../components/RequestSection.vue'

/**
 * Список заявок мобильной части (Ф-ЗАЯ-06): «Мои», «На исполнение», «Все» и под ними «Открытые» или «Закрытые».
 * Заявки из очереди на телефоне показываются первыми в открытых «Моих» и «Всех». Без связи — сохранённый список.
 */

const scopes: { id: RequestScope; text: string }[] = [
  { id: 'mine', text: 'Мои' },
  { id: 'todo', text: 'На исполнение' },
  { id: 'all', text: 'Все' },
]
const closedOptions = [
  { id: false, text: 'Открытые' },
  { id: true, text: 'Закрытые' },
]

const scope = ref<RequestScope>('mine')
const closed = ref(false)

const requests = shallowRef<RepairRequest[]>([])
const stale = ref(false)
const error = ref('')

const queued = computed(() => {
  const u = currentUser.value
  if (!u || closed.value || scope.value === 'todo') return []
  return outbox.value.filter((x) => x.author === u.id).map((x) => outboxAsRequest(x, u.name))
})

let seq = 0
async function load() {
  const my = ++seq
  error.value = ''
  try {
    const key = `requests:${scope.value}:${closed.value}`
    const result = await loadCached(key, () =>
      fetchRequests(requestFilter(scope.value, { closed: closed.value }), null),
    )
    if (my !== seq) return
    requests.value = result.data
    stale.value = result.stale
  } catch (e) {
    if (my !== seq) return
    requests.value = []
    error.value = e instanceof Error ? t(e.message) : String(e)
  }
}

watch([scope, closed], load)

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
.requests__title {
  font-size: 1.5rem;
  font-weight: 700;
}

.requests__pills {
  display: flex;
  gap: 0.5rem;
  overflow-x: auto;
}

.requests__pill {
  flex: none;
  height: 2.5rem;
  padding: 0 1rem;
  border: 0.0625rem solid $zs-border;
  border-radius: 62.5rem;
  background: #fff;
  color: $zs-text;
  font: inherit;
  font-size: 0.875rem;
  font-weight: 600;
  white-space: nowrap;
  cursor: pointer;

  &--small {
    height: 2.1rem;
    font-size: 0.8125rem;
    font-weight: 500;
  }

  &--on {
    border-color: $primary;
    background: $primary;
    color: #fff;
  }
}

.requests__stale {
  color: #7a5200;
  font-size: 0.8125rem;
}

.requests__error {
  padding: 0.625rem 0.875rem;
  border-radius: 0.25rem;
  background: #fbe5e1;
  color: #8f2416;
  font-size: 0.8125rem;
}
</style>
