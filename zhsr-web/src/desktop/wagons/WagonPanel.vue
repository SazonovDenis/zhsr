<template>
  <SidePanel
    :empty="wagon ? '' : 'Выберите вагон в списке'"
    :title="wagon ? t('Вагон № {number}', { number: wagon.number }) : ''"
    :subtitle="
      wagon
        ? `${dictManager.getValue('WagonType', wagon.wagonType)} • ${dictManager.getValue('WagonModel', wagon.modelType)}`
        : ''
    "
    :closable="!journal"
    @close="emit('close')"
  >
    <SidePanelFields v-if="wagon">
      <span>{{ t('Расположение') }}</span>
      <span>
        {{ wagonPlace(wagon) }}
        <span class="zs-sub wagon-panel__since">{{ sinceText(wagon.dbegLocation, nowMinute) }}</span>
      </span>
      <span>{{ t('Состояние') }}</span>
      <span>
        <DictBadge :id="wagon.wagonStatusType" dict="WagonState" />
        <span class="zs-sub wagon-panel__since">{{ sinceText(wagon.dbegStatus, nowMinute) }}</span>
      </span>
      <template v-if="wagon.pendingTransfer">
        <span>{{ t('Ожидает приёма') }}</span>
        <span>
          {{ t('Акт № {n}', { n: wagon.pendingTransfer.number }) }}
          <span class="zs-sub wagon-panel__since">→ {{ transferPlaceText(pendingTo(wagon.pendingTransfer)) }}</span>
        </span>
      </template>
      <span>{{ t('Изготовитель') }}</span>
      <span>
        {{ wagon.maker || '—'
        }}<template v-if="wagon.buildYear"> • {{ t('{year} г.', { year: wagon.buildYear }) }}</template>
      </span>
      <span>{{ t('Ввод в эксплуатацию') }}</span>
      <span>{{ isoDate(wagon.dbeg) }}</span>
      <span>{{ t('Депо приписки') }}</span>
      <span>{{ dictManager.getValue('Depot', wagon.depot) }}</span>
      <template v-if="wagon.dend">
        <span>{{ t('Вывод из эксплуатации') }}</span>
        <span>{{ isoDate(wagon.dend) }}</span>
      </template>
    </SidePanelFields>
    <!-- рейсы вагона: выполняется и ближайший в плане — ссылкой на формирование рейса -->
    <div v-if="wagon && shownTrips.length > 0" class="wagon-panel__section q-mt-md">
      <div class="wagon-panel__section-title">{{ t('Рейсы') }}</div>
      <div v-for="tr in shownTrips" :key="tr.trip.id" class="wagon-panel__trip">
        <button type="button" class="wagon-panel__link q-pa-none" @click="openTrip(tr.trip.id)">
          {{ tripRefDatedText(tr.trip) }}
        </button>
        <DictBadge :id="tr.tripStatusType" dict="TripState" />
      </div>
    </div>
    <!-- переходы в списки с фильтром по этому вагону; число — сколько там будет строк -->
    <div v-if="wagon" class="wagon-panel__links q-mt-md">
      <SoftRouteLink
        text="Рейсы вагона ({n})"
        :params="{ n: wagon.trips.length }"
        :to="{ name: 'trips', query: { wagon: wagon.number } }"
      />
      <SoftRouteLink
        :text="requestCount === null ? 'Заявки вагона' : 'Заявки вагона ({n})'"
        :params="{ n: requestCount }"
        :to="{ name: 'requests', query: { wagon: String(wagon.id), number: wagon.number } }"
      />
    </div>
  </SidePanel>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'

import { fetchRequestCounts } from '@/api/requests'
import type { WagonPendingTransfer, WagonRow } from '@/api/wagons'
import { t } from '@/boot/i18n'
import DictBadge from '@/shared/components/DictBadge.vue'
import SoftRouteLink from '@/shared/components/form/SoftRouteLink.vue'
import { dictManager } from '@/shared/dict/DictManager'
import { wagonRequestsFilter } from '@/shared/requests/requestView'
import { nowMinute, sinceText } from '@/shared/since'
import { transferPlaceText } from '@/shared/transferPlace'
import { TripStatus } from '@/shared/constants/systemDicts'
import { tripRefDatedText } from '@/shared/trips/tripRefText'
import { wagonPlace } from '@/shared/wagonPlace'
import SidePanel from '../frame/SidePanel.vue'
import SidePanelFields from '../frame/SidePanelFields.vue'

/**
 * Правая панель списка вагонов и журнала вагона: карточка выбранного вагона — депо приписки, расположение и состояние
 * с длительностью, ожидающий акт передачи, паспорт; ниже блок «Рейсы» — выполняющийся и ближайший в плане; ссылки «Рейсы вагона» и «Заявки
 * вагона» — в списки с фильтром по вагону. Действия с вагоном — в меню строки списка (wagonActions). Справочники Station, Depot и Yard
 * загружает страница.
 */
const props = withDefaults(
  defineProps<{
    wagon: WagonRow | null
    /** панель на странице журнала вагона: без крестика */
    journal?: boolean
  }>(),
  { journal: false },
)

const emit = defineEmits<{ close: [] }>()

const router = useRouter()

/** ISO-дата «2026-10-30» → «30.10.2026 г.»; пусто — прочерк. */
const isoDate = (iso: string | null) => (iso ? t('{date} г.', { date: iso.split('-').reverse().join('.') }) : '—')

/** Куда передаётся вагон по ожидающему акту. */
const pendingTo = (a: WagonPendingTransfer) => ({ station: a.toStation, depot: a.toDepot, yard: a.toYard })

/** Рейсы на панели: выполняющиеся и ближайший в плане (рейсы приходят по плановому отправлению). */
const shownTrips = computed(() => {
  const trips = props.wagon?.trips ?? []
  const planned = trips.find((tr) => tr.tripStatusType === TripStatus.PLANNED)
  return [...trips.filter((tr) => tr.tripStatusType === TripStatus.IN_PROGRESS), ...(planned ? [planned] : [])]
})

// число у ссылки «Заявки вагона» — по тому же фильтру, что у перехода; null — ещё не посчитано или не удалось
const requestCount = ref<number | null>(null)
watch(
  () => props.wagon?.id,
  async (id) => {
    requestCount.value = null
    if (!id) return
    try {
      const counts = await fetchRequestCounts(wagonRequestsFilter(id))
      if (props.wagon?.id === id)
        requestCount.value = Object.values(counts.requestStatusType).reduce((a, b) => a + b, 0)
    } catch {
      // счётчик: ошибку осознанно не показываем — ссылка остаётся без числа
    }
  },
  { immediate: true },
)

function openTrip(id: number) {
  void router.push({ name: 'trip-form', params: { id } })
}
</script>

<style scoped lang="scss">
.wagon-panel__since {
  display: block;
}

.wagon-panel__link {
  border: 0;
  background: none;
  color: $primary;
  font: inherit;
  text-align: left;
  cursor: pointer;
}

.wagon-panel__section {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

// подзаголовок блока — как «История» в панели заявки
.wagon-panel__section-title {
  color: $zs-text-muted;
  font-size: 0.8rem;
  font-weight: 600;
  letter-spacing: 0.1em;
  text-transform: uppercase;
}

.wagon-panel__trip {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.wagon-panel__links {
  display: flex;
  flex-wrap: wrap;
  gap: 1rem;
}
</style>
