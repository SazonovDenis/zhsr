<template>
  <DesktopFrame v-model:floating="panel.floating.value" :right-open="panel.open.value">
    <template #header>
      <h1 class="desktop-frame__title">{{ t('Акты передачи') }}</h1>
      <div v-if="canTransfer" class="desktop-frame__actions">
        <button type="button" class="zs-btn-primary" @click="newAct(TransferDirection.FROM_DEPOT)">
          {{ t('Приём на станцию') }}
        </button>
        <button type="button" class="zs-btn-primary" @click="newAct(TransferDirection.TO_DEPOT)">
          {{ t('Передача со станции') }}
        </button>
      </div>
    </template>

    <FilterBar>
      <CbButton v-model="states" label="Состояние" :values="stateChoices" :max-button-count="4" />
      <template #right>{{ t('{n} актов', rows.length) }}</template>
    </FilterBar>

    <DataTable
      v-model:selected="selectedId"
      :columns="columns"
      :rows="rows"
      :error="loadError"
      :retry="load"
      row-key="id"
      :loading="busy.active.value"
      empty="Актов по выбранному фильтру нет"
    />

    <template #right>
      <SidePanel
        :empty="selected ? '' : 'Выберите акт в списке'"
        :title="selected ? t('Акт № {n}', { n: selected.number }) : ''"
        :subtitle="selected ? directionText(selected) : ''"
        @close="panel.close"
      >
        <template v-if="selected">
          <SidePanelFields>
            <span>{{ t('Станция') }}</span>
            <span>{{ dictManager.getValue('Station', selected.station) }}</span>
            <span>{{ t('Депо или парк отстоя') }}</span>
            <span>{{ placeName(selected) }}</span>
            <span>{{ t('Вид работ') }}</span>
            <span>{{ dictManager.getValue('WorkType', selected.workType) }}</span>
            <span>{{ t('Состояние') }}</span>
            <span>{{ statusText(selected.actStatusType) }}</span>
            <span>{{ t('Сдал') }}</span>
            <span>{{ selected.transferredByUsr.name }} • {{ formatDateTime(selected.transferredDt) }}</span>
            <template v-if="selected.receivedByUsr">
              <span>{{ t('Принял') }}</span>
              <span>{{ selected.receivedByUsr.name }} • {{ formatDateTime(selected.receivedDt) }}</span>
            </template>
            <template v-if="selected.rejectReason">
              <span>{{ t('Причина отказа') }}</span>
              <span>{{ selected.rejectReason }}</span>
            </template>
          </SidePanelFields>
          <div class="acts__wagons">
            <div v-for="w in selected.wagons" :key="w.number" class="acts__wagon q-pa-sm">
              <span class="zs-mono">{{ w.number }}</span>
              <span class="zs-sub">{{ w.withProperty ? t('с имуществом') : t('без имущества') }}</span>
            </div>
          </div>
          <ErrorBox v-if="actionError" :error="actionError" />
          <div v-if="selected.canReceive" class="acts__actions">
            <button type="button" class="zs-btn-primary" :disabled="running" @click="accept">
              {{ t('Принять вагоны') }}
            </button>
            <button type="button" class="acts__btn q-py-none q-px-md" :disabled="running" @click="reject">
              {{ t('Отказать в приёме') }}
            </button>
          </div>
        </template>
      </SidePanel>
    </template>
  </DesktopFrame>
</template>

<script setup lang="ts">
import { Notify } from 'quasar'
import { computed, ref, shallowRef, watch } from 'vue'
import { useRouter } from 'vue-router'

import type { Transfer } from '@/api/transfers'
import { acceptTransfer, fetchTransfers } from '@/api/transfers'
import { t } from '@/boot/i18n'
import { provideBusy } from '@/shared/busy'
import CbButton from '@/shared/components/choice/CbButton.vue'
import { openDialog } from '@/shared/components/dialog/openDialog'
import ErrorBox from '@/shared/components/ErrorBox.vue'
import FilterBar from '@/shared/components/FilterBar.vue'
import DataTable from '@/shared/components/table/DataTable.vue'
import type { RowKey } from '@/shared/components/table/types'
import { defineColumns } from '@/shared/components/table/types'
import { ActStatus, RoleType, TransferDirection } from '@/shared/constants/systemDicts'
import { dictManager } from '@/shared/dict/DictManager'
import type { ShownError } from '@/shared/errors'
import { shownError } from '@/shared/errors'
import { formatDateTime } from '@/shared/format'
import { currentUser, hasRole, loadCurrentUser } from '@/shared/user'
import DesktopFrame from '../frame/DesktopFrame.vue'
import SidePanel from '../frame/SidePanel.vue'
import SidePanelFields from '../frame/SidePanelFields.vue'
import { useSidePanel } from '../frame/useSidePanel'
import ActRejectDialog from './ActRejectDialog.vue'

/** Акты приёма-передачи (Ф-АКТ-02): список, оформление новых, приём вагонов и отказ. По умолчанию — ожидают приёма. */
const router = useRouter()
// общее ожидание страницы: спиннер на таблице — на всю цепочку загрузок
const busy = provideBusy()

const STATUS_TEXT: Record<number, string> = {
  [ActStatus.PENDING]: 'Ожидает приёма',
  [ActStatus.DONE]: 'Проведён',
  [ActStatus.REJECTED]: 'Отказ',
  [ActStatus.CANCELLED]: 'Отменён',
}
const statusText = (status: number) => t(STATUS_TEXT[status] ?? '')
const stateChoices = Object.entries(STATUS_TEXT).map(([id, text]) => ({ id: Number(id), text }))

const directionText = (a: Transfer) =>
  a.transferDirectionType === TransferDirection.FROM_DEPOT ? t('Приём на станцию') : t('Передача со станции')
const placeName = (a: Transfer) =>
  a.depot !== null ? dictManager.getValue('Depot', a.depot) : dictManager.getValue('Yard', a.yard)

const columns = defineColumns<Transfer>([
  { field: 'number', title: 'Номер', width: '7rem' },
  { field: 'transferDirectionType', title: 'Направление', format: (_, row) => directionText(row) },
  { field: 'station', title: 'Станция', format: (v) => dictManager.getValue('Station', v as number) },
  { field: 'depot', title: 'Депо или парк отстоя', format: (_, row) => placeName(row) },
  { field: 'wagons', title: 'Вагонов', width: '6rem', format: (_, row) => String(row.wagons.length) },
  { field: 'transferredDt', title: 'Сдано', format: (v) => formatDateTime(v as string) },
  { field: 'actStatusType', title: 'Состояние', format: (v) => statusText(v as number) },
])

const states = ref<number[]>([ActStatus.PENDING])
const rows = shallowRef<Transfer[]>([])
const selectedId = ref<RowKey | null>(null)
const selected = computed(() => rows.value.find((row) => row.id === selectedId.value) ?? null)
const panel = useSidePanel(selectedId)
const loadError = ref<ShownError | null>(null)
const actionError = ref<ShownError | null>(null)
const running = ref(false)

const canTransfer = computed(() =>
  [RoleType.ADMIN, RoleType.RECEIVER, RoleType.MASTER].some((r) => hasRole(currentUser.value, r)),
)

async function load() {
  loadError.value = null
  try {
    await busy.run(async () => {
      const [list] = await Promise.all([
        fetchTransfers(states.value),
        dictManager.prepareDicts('Station', 'Depot', 'Yard'),
        loadCurrentUser(),
      ])
      rows.value = list
    })
  } catch (e) {
    rows.value = []
    loadError.value = shownError(e)
  }
}
watch(states, load, { deep: true })
void load()

function newAct(dir: number) {
  void router.push({ name: 'transfer', query: { dir } })
}

async function accept() {
  const act = selected.value
  if (!act) return
  actionError.value = null
  running.value = true
  try {
    await acceptTransfer(act.id)
    Notify.create({ message: t('Вагоны приняты'), type: 'positive', position: 'bottom' })
    await load()
  } catch (e) {
    actionError.value = shownError(e)
  } finally {
    running.value = false
  }
}

async function reject() {
  const act = selected.value
  if (!act) return
  const done = await openDialog<Transfer>(ActRejectDialog, { actId: act.id })
  if (!done) return
  Notify.create({ message: t('В приёме отказано'), position: 'bottom' })
  await load()
}
</script>

<style scoped lang="scss">
.acts__wagons {
  border: 0.0625rem solid $zs-border;
  border-radius: 0.3rem;
}

.acts__wagon {
  display: flex;
  justify-content: space-between;
  gap: 0.5rem;
  border-top: 0.0625rem solid $zs-border-row;
  font-size: 0.8rem;

  &:first-child {
    border-top: 0;
  }
}

.acts__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
}

.acts__btn {
  height: var(--zs-control-height);
  border: 0.0625rem solid $zs-border-input;
  border-radius: 0.3rem;
  background: #fff;
  color: $primary;
  font: inherit;
  font-size: 0.8rem;
  font-weight: 600;
  cursor: pointer;
}
</style>
