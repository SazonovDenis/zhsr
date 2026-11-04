<template>
  <DesktopFrame :back="back">
    <template #header>
      <h1 class="desktop-frame__title">
        {{ fromDepot ? t('Приём вагонов на станцию') : t('Передача вагонов со станции') }}
      </h1>
      <div class="desktop-frame__actions">
        <button type="button" class="zs-btn-primary" :disabled="!canSave" @click="save">{{ okText }}</button>
      </div>
    </template>

    <ErrorBox v-if="error" :error="error" class="transfer__error" />
    <LoadSpinner v-if="busy.active.value" />

    <div v-if="ready" class="transfer">
      <div class="transfer__act">
        <div class="transfer__caption">{{ t('Акт') }}</div>
        <CbSegment
          :model-value="direction"
          single
          required
          :values="directions"
          @update:model-value="setDirection($event as number)"
        />
        <FormField :label="fromDepot ? 'Станция приёма' : 'Станция передачи'">
          <CbList
            single
            no-search
            placeholder="Выберите…"
            :model-value="station"
            :values="stations"
            @update:model-value="setStation($event as number | null)"
          />
        </FormField>
        <FormField :label="fromDepot ? 'Откуда' : 'Куда'">
          <CbList
            single
            no-search
            placeholder="Выберите…"
            :model-value="target"
            :values="targets"
            @update:model-value="setTarget($event as string | null)"
          />
        </FormField>
        <FormField label="Вид работ">
          <CbList v-model="work" single no-search placeholder="Выберите…" :values="dictChoices('WorkType')" />
        </FormField>
        <FormField label="Время акта">
          <DatePicker v-model="time" with-time />
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
          <div v-for="w in wagons" :key="w.number" class="transfer__row">
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
          <div v-if="!wagonsBusy && wagons.length === 0" class="transfer__empty">{{ emptyText }}</div>
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
import type { StaffCandidate } from '@/api/trips'
import { t } from '@/boot/i18n'
import { provideBusy } from '@/shared/busy'
import CbList from '@/shared/components/choice/CbList.vue'
import CbSegment from '@/shared/components/choice/CbSegment.vue'
import DictBadge from '@/shared/components/DictBadge.vue'
import ErrorBox from '@/shared/components/ErrorBox.vue'
import FormField from '@/shared/components/form/FormField.vue'
import LoadSpinner from '@/shared/components/LoadSpinner.vue'
import DatePicker from '@/shared/components/period/DatePicker.vue'
import { ActStatus, RoleType, TransferDirection } from '@/shared/constants/systemDicts'
import { dictChoices } from '@/shared/dict/dictChoices'
import { dictManager } from '@/shared/dict/DictManager'
import type { ShownError } from '@/shared/errors'
import { shownError } from '@/shared/errors'
import { useLoad } from '@/shared/loadable'
import { currentUser, hasRole, loadCurrentUser } from '@/shared/user'
import { placeText } from '@/shared/wagonPlace'
import DesktopFrame from '../frame/DesktopFrame.vue'

/**
 * Оформление акта приёма-передачи (Ф-АКТ-02). Акт принимающей стороны и администратора проводится сразу, с указанием,
 * кто сдал вагоны; акт передающей стороны ждёт приёма. Запрос страницы может заранее задать акт: dir, station, depot,
 * yard, wagons (через запятую) — так открывает его «Вернуть на станцию» из прицепки и «Оформить передачу» из
 * формирования рейса. Проверки — на сервере.
 */
const route = useRoute()
const router = useRouter()
// общее ожидание страницы: спиннер на месте содержимого — на всю цепочку загрузок
const busy = provideBusy()

const num = (v: unknown) => (v ? Number(v) : null)
const query = route.query

const direction = ref<number>(num(query.dir) ?? TransferDirection.TO_DEPOT)
const station = ref<number | null>(num(query.station))
const target = ref<string | null>(
  query.depot ? `d${String(query.depot)}` : query.yard ? `y${String(query.yard)}` : null,
)
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

const fromDepot = computed(() => direction.value === TransferDirection.FROM_DEPOT)
const directions = computed(() => [
  { id: TransferDirection.FROM_DEPOT, text: 'Приём на станцию' },
  { id: TransferDirection.TO_DEPOT, text: 'Передача со станции' },
])

const placesAt = (name: 'Depot' | 'Yard', id: number) =>
  dictManager.getList(name).filter((r) => (r as { station?: number }).station === id)

// станции, при которых есть депо или парк отстоя
const stations = computed(() => {
  if (!ready.value) return []
  const ids = new Set(
    [...dictManager.getList('Depot'), ...dictManager.getList('Yard')].map((r) => (r as { station?: number }).station),
  )
  return [...ids].filter((id): id is number => !!id).map((id) => ({ id, text: dictManager.getValue('Station', id) }))
})

const targets = computed(() => {
  if (!ready.value || !station.value) return []
  return [
    ...placesAt('Depot', station.value).map((r) => ({
      id: `d${r.id}`,
      text: `${dictManager.getValue('Depot', r.id)} • ${t('депо')}`,
    })),
    ...placesAt('Yard', station.value).map((r) => ({
      id: `y${r.id}`,
      text: `${dictManager.getValue('Yard', r.id)} • ${t('парк отстоя')}`,
    })),
  ]
})
const depot = computed(() => (target.value?.startsWith('d') ? Number(target.value.slice(1)) : null))
const yard = computed(() => (target.value?.startsWith('y') ? Number(target.value.slice(1)) : null))

// принимающая сторона и администратор проводят акт сразу
const immediate = computed(() => {
  const u = currentUser.value
  if (hasRole(u, RoleType.ADMIN)) return true
  const taker = fromDepot.value ? RoleType.RECEIVER : RoleType.MASTER
  return hasRole(u, taker) && u?.depotStation === station.value
})

const okText = computed(() => (immediate.value ? t('Оформить и принять') : t('Оформить акт')))

const emptyText = computed(() => {
  if (!station.value) return t('Выберите станцию')
  if (fromDepot.value && !target.value) return t('Выберите, откуда принимаются вагоны: депо или парк отстоя')
  return t('Вагонов нет')
})

const canSave = computed(
  () =>
    !running.value &&
    !!station.value &&
    !!target.value &&
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
const givers = shallowRef<StaffCandidate[]>([])
const giverChoices = computed(() => givers.value.map((g) => ({ id: g.usr, text: g.name })))
const giverLoad = useLoad(async () => {
  givers.value = station.value ? await fetchTransferGivers(direction.value, station.value) : []
})

async function loadWagons() {
  wagonsBusy.value = true
  try {
    wagons.value = await fetchTransferWagons(direction.value, station.value, depot.value, yard.value)
    const shown = wagons.value.map((w) => w.number)
    selected.value = selected.value.filter((n) => shown.includes(n))
  } catch (e) {
    error.value = shownError(e)
  } finally {
    wagonsBusy.value = false
  }
}

function reload() {
  giver.value = null
  void loadWagons()
  if (immediate.value) void giverLoad.run()
}

function setDirection(value: number) {
  direction.value = value
  selected.value = []
  property.value = []
  reload()
}

function setStation(value: number | null) {
  station.value = value
  target.value = null
  selected.value = []
  property.value = []
  reload()
}

function setTarget(value: string | null) {
  target.value = value
  void loadWagons()
}

watch(immediate, (now) => {
  if (now) void giverLoad.run()
})

void busy
  .run(async () => {
    const [user] = await Promise.all([
      loadCurrentUser(),
      dictManager.prepareDicts('Station', 'Depot', 'Yard', 'WagonType'),
    ])
    station.value ??= user.depotStation
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
      transferDirectionType: direction.value,
      station: station.value!,
      depot: depot.value,
      yard: yard.value,
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
  gap: 1.25rem;
  min-height: 0;
}

.transfer__error {
  margin-bottom: 0.75rem;
}

.transfer__act,
.transfer__list {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  min-width: 0;
}

.transfer__caption {
  color: $zs-text-muted;
  font-size: 0.75rem;
  font-weight: 600;
  letter-spacing: 0.06em;
  text-transform: uppercase;
}

.transfer__muted {
  color: $zs-text-muted;
  font-size: 0.8125rem;
}

.transfer__table {
  overflow-y: auto;
  border: 0.0625rem solid $zs-border;
  border-radius: 0.25rem;
  background: #fff;
}

.transfer__row {
  display: grid;
  grid-template-columns: auto 5rem 8rem 1fr auto auto;
  align-items: center;
  gap: 0.625rem;
  padding: 0.375rem 0.75rem;
  border-top: 0.0625rem solid $zs-border-row;
  font-size: 0.8125rem;

  &:first-child {
    border-top: 0;
  }
}

.transfer__empty {
  padding: 1rem 0.75rem;
  color: $zs-text-muted;
  font-size: 0.8125rem;
}
</style>
