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
      <span>{{ t('Ответственные') }}</span>
      <span>
        <UserNames :users="wagon.responsibleUsrs" />
        <!-- сменить или снять ответственных без акта — окно с галочками; кнопка видна всегда, нельзя —
             неактивна, причина во всплывающей подсказке -->
        <q-btn
          unelevated
          outline
          dense
          no-caps
          color="primary"
          class="zs-soft-border q-ml-sm"
          padding="0.1rem 0.6rem"
          :disable="!!responsibleReason"
          :title="responsibleReason || undefined"
          @click="changeResponsible"
        >
          {{ wagon.responsibleUsrs.length > 0 ? t('Сменить') : t('Назначить') }}
        </q-btn>
        <span v-if="wagon.dbegResponsible" class="zs-sub wagon-panel__since">{{
          sinceText(wagon.dbegResponsible, nowMinute)
        }}</span>
      </span>
      <span>{{ t('Пробег') }}</span>
      <span>{{ t('{km} км', { km: formatThousands(wagon.runKm) }) }}</span>
      <span>{{ t('Поезд') }}</span>
      <span>
        <SoftRouteLink
          v-if="wagon.train"
          text="{name}"
          :params="{ name: wagon.train.name }"
          :to="{ name: 'train-form', params: { id: wagon.train.id } }"
        />
        <template v-else>{{ t('не в поезде') }}</template>
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
    <!-- рейсы поезда вагона: выполняется и ближайший в плане — ссылкой на формирование рейса -->
    <TripLinks v-if="wagon" :trips="shownTrips" class="q-mt-md" />
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
      <SoftRouteLink
        :text="checkCount === null ? 'Проверки вагона' : 'Проверки вагона ({n})'"
        :params="{ n: checkCount }"
        :to="{ name: 'checks', query: { wagon: String(wagon.id), number: wagon.number } }"
      />
      <SoftRouteLink
        :text="equipCount === null ? 'Описи вагона' : 'Описи вагона ({n})'"
        :params="{ n: equipCount }"
        :to="{ name: 'equip', query: { wagon: String(wagon.id), number: wagon.number } }"
      />
      <!-- последняя опись вагона — в окне, без перехода -->
      <SoftLink text="Посмотреть список имущества" @click="openEquip" />
    </div>
  </SidePanel>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'

import { fetchCheckLists } from '@/api/checks'
import { fetchEquipLists } from '@/api/equip'
import { fetchRequestCounts } from '@/api/requests'
import type { WagonPendingTransfer, WagonRow } from '@/api/wagons'
import { changeWagonResponsible } from '@/api/wagons'
import { t } from '@/boot/i18n'
import DictBadge from '@/shared/components/DictBadge.vue'
import SoftLink from '@/shared/components/form/SoftLink.vue'
import SoftRouteLink from '@/shared/components/form/SoftRouteLink.vue'
import { dictManager } from '@/shared/dict/DictManager'
import { wagonRequestsFilter, wagonRequestsPeriod } from '@/shared/requests/requestView'
import { nowMinute, sinceText } from '@/shared/since'
import { transferPlaceText } from '@/shared/transferPlace'
import { TripStatus } from '@/shared/constants/systemDicts'
import { formatThousands } from '@/shared/format'
import { wagonPlace } from '@/shared/wagonPlace'
import UserNames from '@/shared/components/UserNames.vue'
import { pickResponsibles } from './responsibleAssign'
import { changeResponsibleReason, wagonStation } from './wagonActions'
import SidePanel from '../frame/SidePanel.vue'
import SidePanelFields from '../frame/SidePanelFields.vue'
import TripLinks from '../trips/TripLinks.vue'

/**
 * Правая панель списка вагонов и журнала вагона: карточка выбранного вагона — депо приписки, расположение, состояние и
 * ответственный с длительностью, общий пробег, ожидающий акт передачи, паспорт; ниже блок «Рейсы» — выполняющийся и ближайший в плане; ссылки «Рейсы вагона» и «Заявки
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

const emit = defineEmits<{ close: []; changed: [wagon: WagonRow] }>()

// ответственный без акта (Ф-ВАГ-07): почему нельзя; пусто — можно
const responsibleReason = computed(() => (props.wagon ? changeResponsibleReason(props.wagon) : ''))

// ответственные — во всплывающем окне с галочками: окно и сохраняет, ошибка — в окне; ответ сервера — вагон после
// смены (событие changed)
async function changeResponsible() {
  const w = props.wagon
  if (!w) return
  await pickResponsibles({
    station: wagonStation(w),
    wagons: [w.number],
    current: w.responsibleUsrs,
    save: async (usrs) => emit('changed', await changeWagonResponsible(w.id, usrs)),
  })
}

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

// числа у ссылок «Проверки вагона» и «Описи вагона» — за месяц, как в списках по переходу; null — ещё не посчитано
// или не удалось
const checkCount = ref<number | null>(null)
const equipCount = ref<number | null>(null)
watch(
  () => props.wagon?.id,
  async (id) => {
    checkCount.value = null
    equipCount.value = null
    if (!id) return
    const filter = { template: null, wagon: id, query: '', ...wagonRequestsPeriod() }
    try {
      const [checks, lists] = await Promise.all([fetchCheckLists(filter), fetchEquipLists(filter)])
      if (props.wagon?.id !== id) return
      checkCount.value = checks.length
      equipCount.value = lists.length
    } catch {
      // счётчики: ошибку осознанно не показываем — ссылки остаются без чисел
    }
  },
  { immediate: true },
)

/** Список имущества: последняя опись вагона на отдельной странице. */
function openEquip() {
  if (props.wagon)
    void router.push({ name: 'wagon-equip', params: { id: props.wagon.id }, query: { number: props.wagon.number } })
}
</script>

<style scoped lang="scss">
.wagon-panel__since {
  display: block;
}

// подзаголовок блока — как «История» в панели заявки

.wagon-panel__links {
  display: flex;
  flex-wrap: wrap;
  gap: 1rem;
}
</style>
