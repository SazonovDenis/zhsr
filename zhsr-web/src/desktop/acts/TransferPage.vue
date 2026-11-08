<template>
  <DesktopFrame :back="back">
    <template #header>
      <h1 class="desktop-frame__title">{{ t('Передача вагонов') }}</h1>
      <div class="desktop-frame__actions">
        <button type="button" class="zs-btn-primary" :disabled="!canSave" @click="save">{{ okText }}</button>
      </div>
    </template>

    <ErrorBox v-if="error" :error="error" class="transfer__error q-mb-md" />
    <LoadSpinner v-if="busy.active.value" />

    <div v-if="ready" class="transfer">
      <div class="transfer__act">
        <div class="transfer__caption">{{ t('Акт') }}</div>
        <FormField label="Станция">
          <CbList
            single
            no-search
            placeholder="Выберите…"
            :model-value="station"
            :values="stations"
            @update:model-value="setStation($event as number | null)"
          />
        </FormField>
        <FormField label="Откуда">
          <CbList
            single
            no-search
            placeholder="Выберите…"
            :model-value="fromCode"
            :values="fromChoices"
            @update:model-value="setFrom($event as string | null)"
          />
        </FormField>
        <FormField label="Куда">
          <CbList v-model="toCode" single no-search placeholder="Выберите…" :values="toChoices" />
        </FormField>
        <FormField label="Вид работ">
          <CbList v-model="work" single no-search placeholder="Выберите…" :values="dictChoices('WorkType')" />
        </FormField>
        <FormField label="Время акта">
          <DatePicker v-model="time" with-time date-text="Время акта" />
        </FormField>
        <FormField v-if="immediate" label="Кто сдал вагоны">
          <CbList v-model="giver" single no-search placeholder="Выберите…" :values="giverChoices" :source="giverLoad" />
        </FormField>
        <div class="transfer__muted">
          {{
            immediate
              ? t('Акт будет проведён сразу.')
              : t('Акт будет ждать приёма. Расположение вагонов изменится при приёме.')
          }}
        </div>
      </div>

      <div class="transfer__list">
        <div class="transfer__caption">{{ t('Вагоны') }}</div>
        <div class="transfer__table">
          <div v-for="w in wagons" :key="w.number" class="transfer__row q-py-sm q-px-md">
            <q-checkbox v-model="selected" :val="w.number" :disable="!!w.pendingAct" dense />
            <span class="zs-mono">{{ w.number }}</span>
            <span>{{ dictManager.getValue('WagonType', w.wagonType) }}</span>
            <span class="zs-sub">
              {{
                w.pendingAct ? t('ожидает приёма, акт № {n}', { n: w.pendingAct }) : placeText(w.locationType, w.place)
              }}
            </span>
            <DictBadge :id="w.wagonStatusType" dict="WagonState" />
            <q-checkbox v-model="property" :val="w.number" :disable="!selected.includes(w.number)" dense>
              {{ t('с имуществом') }}
            </q-checkbox>
          </div>
          <div v-if="!wagonsBusy && wagons.length === 0" class="transfer__empty q-pa-md">{{ emptyText }}</div>
        </div>
      </div>
    </div>
  </DesktopFrame>
</template>

<script setup lang="ts">
import { date, Notify } from 'quasar'
import { computed, ref, shallowRef, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import type { TransferWagon } from '@/api/transfers'
import { createTransfer, fetchTransferGivers, fetchTransferWagons } from '@/api/transfers'
import type { UserRef } from '@/api/requests'
import { t } from '@/boot/i18n'
import { provideBusy } from '@/shared/busy'
import CbList from '@/shared/components/choice/CbList.vue'
import DictBadge from '@/shared/components/DictBadge.vue'
import ErrorBox from '@/shared/components/ErrorBox.vue'
import FormField from '@/shared/components/form/FormField.vue'
import LoadSpinner from '@/shared/components/LoadSpinner.vue'
import DatePicker from '@/shared/components/period/DatePicker.vue'
import { ActStatus, RoleType } from '@/shared/constants/systemDicts'
import { dictChoices } from '@/shared/dict/dictChoices'
import { dictManager } from '@/shared/dict/DictManager'
import type { ShownError } from '@/shared/errors'
import { shownError } from '@/shared/errors'
import { useLoad } from '@/shared/loadable'
import { placeOfCode, placeStation, stationPlaces } from '@/shared/transferPlace'
import { currentUser, hasRole, loadCurrentUser } from '@/shared/user'
import { placeText } from '@/shared/wagonPlace'
import DesktopFrame from '../frame/DesktopFrame.vue'

/**
 * Оформление акта приёма-передачи (Ф-АКТ-02): откуда и куда — разные расположения одной станции (сама станция, её депо
 * и парки отстоя), со станции на станцию не передаётся. Акт принимающей стороны и администратора проводится сразу, с
 * указанием, кто сдал вагоны; акт передающей стороны ждёт приёма. Запрос страницы может заранее задать акт: from и to —
 * коды расположений (shared/transferPlace.ts), wagons — номера вагонов через запятую; так открывают акт кнопки панели
 * вагона, «Вернуть на станцию» из прицепки и «Оформить передачу» из формирования рейса. Проверки — на сервере.
 */
const route = useRoute()
const router = useRouter()
// общее ожидание страницы: спиннер на месте содержимого — на всю цепочку загрузок
const busy = provideBusy()

const query = route.query
const str = (v: unknown) => (typeof v === 'string' && v ? v : null)

const station = ref<number | null>(null)
const fromCode = ref<string | null>(str(query.from))
const toCode = ref<string | null>(str(query.to))
const work = ref<number | null>(null)
const time = ref<string | null>(date.formatDate(new Date(), 'YYYY-MM-DDTHH:mm'))
const giver = ref<number | null>(null)
const wagons = shallowRef<TransferWagon[]>([])
const selected = ref<string[]>(query.wagons ? String(query.wagons).split(',') : [])
const property = ref<string[]>([])
const ready = ref(false)
const running = ref(false)
const wagonsBusy = ref(false)
const error = ref<ShownError | null>(null)

const from = computed(() => placeOfCode(fromCode.value))
const to = computed(() => placeOfCode(toCode.value))

// станции, при которых есть депо или парк отстоя
const stations = computed(() => {
  if (!ready.value) return []
  const ids = new Set(
    [...dictManager.getList('Depot'), ...dictManager.getList('Yard')].map((r) => (r as { station?: number }).station),
  )
  return [...ids].filter((id): id is number => !!id).map((id) => ({ id, text: dictManager.getValue('Station', id) }))
})

// откуда — любое расположение станции; куда — другое расположение, со станции на станцию не передаётся
const fromChoices = computed(() => (ready.value && station.value ? stationPlaces(station.value) : []))
const toChoices = computed(() =>
  fromChoices.value.filter((p) => p.id !== fromCode.value && !(from.value.station !== null && p.id.startsWith('s'))),
)

// принимающая сторона и администратор проводят акт сразу: на станцию принимает приёмщик, иначе мастер депо
const immediate = computed(() => {
  const u = currentUser.value
  if (hasRole(u, RoleType.ADMIN)) return true
  if (!toCode.value) return false
  const taker = to.value.station !== null ? RoleType.RECEIVER : RoleType.MASTER
  return hasRole(u, taker) && u?.depotStation === station.value
})

const okText = computed(() => (immediate.value ? t('Оформить и принять') : t('Оформить акт')))

const emptyText = computed(() => {
  if (!station.value) return t('Выберите станцию')
  if (!fromCode.value) return t('Выберите, откуда передаются вагоны')
  return t('Вагонов нет')
})

const canSave = computed(
  () =>
    !running.value &&
    !!fromCode.value &&
    !!toCode.value &&
    !!work.value &&
    !!time.value &&
    selected.value.length > 0 &&
    (!immediate.value || !!giver.value),
)

const back = () => {
  if (window.history.state?.back) router.back()
  else void router.push({ name: 'acts' })
}

// кто сдал — свой выпадающий список, страница его не ждёт; ошибка — в самом списке
const givers = shallowRef<UserRef[]>([])
const giverChoices = computed(() => givers.value.map((g) => ({ id: g.id, text: g.name })))
const giverLoad = useLoad(async () => {
  givers.value = fromCode.value ? await fetchTransferGivers(from.value) : []
})

async function loadWagons() {
  if (!fromCode.value) {
    wagons.value = []
    return
  }
  wagonsBusy.value = true
  try {
    wagons.value = await fetchTransferWagons(from.value)
    const shown = wagons.value.map((w) => w.number)
    selected.value = selected.value.filter((n) => shown.includes(n))
  } catch (e) {
    error.value = shownError(e)
  } finally {
    wagonsBusy.value = false
  }
}

function setStation(value: number | null) {
  station.value = value
  setFrom(null)
}

// новое «откуда»: другие вагоны и другие сдающие; «куда», совпавшее с ним, сбрасывается
function setFrom(value: string | null) {
  fromCode.value = value
  if (!toChoices.value.some((p) => p.id === toCode.value)) toCode.value = null
  selected.value = []
  property.value = []
  giver.value = null
  givers.value = []
  void loadWagons()
  if (immediate.value) void giverLoad.run()
}

watch(immediate, (now) => {
  if (now) void giverLoad.run()
})

void busy
  .run(async () => {
    const [user] = await Promise.all([
      loadCurrentUser(),
      dictManager.prepareDicts('Station', 'Depot', 'Yard', 'LocationKind', 'WagonType'),
    ])
    // станция — по заданному «откуда», иначе станция депо сотрудника
    station.value = (fromCode.value ? placeStation(from.value) : null) ?? user.depotStation
    ready.value = true
    await loadWagons()
    if (immediate.value) void giverLoad.run()
  })
  .catch((e: unknown) => (error.value = shownError(e)))

async function save() {
  error.value = null
  running.value = true
  try {
    const act = await createTransfer({
      fromStation: from.value.station,
      fromDepot: from.value.depot,
      fromYard: from.value.yard,
      toStation: to.value.station,
      toDepot: to.value.depot,
      toYard: to.value.yard,
      workType: work.value!,
      transferredByUsr: giver.value,
      transferredDt: time.value!,
      wagons: selected.value.map((number) => ({ number, withProperty: property.value.includes(number) })),
    })
    Notify.create({
      message:
        act.actStatusType === ActStatus.DONE ? t('Акт оформлен, вагоны приняты') : t('Акт оформлен и ожидает приёма'),
      type: 'positive',
      position: 'bottom',
    })
    back()
  } catch (e) {
    error.value = shownError(e)
  } finally {
    running.value = false
  }
}
</script>

<style scoped lang="scss">
.transfer {
  display: grid;
  grid-template-columns: 20rem minmax(0, 1fr);
  gap: 1.3rem;
  min-height: 0;
}

.transfer__act,
.transfer__list {
  display: flex;
  flex-direction: column;
  gap: 0.8rem;
  min-width: 0;
}

.transfer__caption {
  color: $zs-text-muted;
  font-size: 0.8rem;
  font-weight: 600;
  letter-spacing: 0.1em;
  text-transform: uppercase;
}

.transfer__muted {
  color: $zs-text-muted;
  font-size: 0.8rem;
}

.transfer__table {
  overflow-y: auto;
  border: 0.0625rem solid $zs-border;
  border-radius: 0.3rem;
  background: #fff;
}

.transfer__row {
  display: grid;
  grid-template-columns: auto 5rem 8rem 1fr auto auto;
  align-items: center;
  gap: 0.6rem;
  border-top: 0.0625rem solid $zs-border-row;
  font-size: 0.8rem;

  &:first-child {
    border-top: 0;
  }
}

.transfer__empty {
  color: $zs-text-muted;
  font-size: 0.8rem;
}
</style>
