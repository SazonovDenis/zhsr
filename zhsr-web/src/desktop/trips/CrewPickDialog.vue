<template>
  <FormDialog
    v-model:show="show"
    height="xl"
    title="Поездная бригада"
    ok-text="Сохранить"
    width="80rem"
    :running="running"
    :error="error"
    @ok="save"
  >
    <FormSection columns="repeat(2, minmax(0, 1fr))">
      <FormField label="Рейс">
        <span class="crew-pick__value">{{ tripName }}</span>
      </FormField>
      <FormField label="Станция отправления">
        <span class="crew-pick__value">{{ station || t('не задана') }}</span>
      </FormField>
    </FormSection>

    <!-- начальник поезда и ПЭМ: «Список ФИО»; занятый в пересекающемся рейсе виден с пометкой, назначить его не даст сервер -->
    <FormSection columns="repeat(2, minmax(0, 1fr))">
      <FormField v-for="d in duties" :key="d.duty" :label="d.title" :title="dutyReason || undefined">
        <CbList
          v-model="d.model.value"
          single
          placeholder="Не назначен"
          :disabled="!!dutyReason"
          :values="d.choices"
          :source="staffLoad"
        >
          <template #option="{ item }">
            <UserOption
              :name="item.text ?? ''"
              :me="isMe({ id: Number(item.id) })"
              :duty="t(d.title)"
              :place="(item.data?.busy as string | undefined) ?? ''"
            />
          </template>
        </CbList>
      </FormField>
    </FormSection>

    <!-- проводники: подпись — как подписи полей, справа бледным — сколько назначено; отступ до таблицы — как у поля -->
    <div class="crew-pick__conductors">
      <div class="crew-pick__caption row items-center justify-between">
        <span>{{ t('Проводники') }}</span>
        <span class="crew-pick__count">{{ t('Назначено проводников в рейс: {n}', { n: selected.length }) }}</span>
      </div>
      <div v-if="conductorReason" class="zs-sub">{{ conductorReason }}</div>
      <!-- таблица, как в списке сотрудников; клик по строке отмечает или снимает -->
      <DataTable
        :columns="conductorColumns"
        :rows="conductorRows"
        row-key="id"
        :loading="loading"
        :empty="emptyText"
        class="crew-pick__table"
        @row-click="onRowClick($event as StaffRow)"
      />
    </div>
  </FormDialog>
</template>

<script setup lang="ts">
import { computed, provide, ref, shallowRef } from 'vue'

import type { StaffRow } from '@/api/staff'
import { fetchStaff } from '@/api/staff'
import type { ConductorCandidate, CrewMember, StaffCandidate } from '@/api/trips'
import { fetchConductorCandidates, fetchStaffCandidates, setTripCrew } from '@/api/trips'
import { t } from '@/boot/i18n'
import { provideBusy } from '@/shared/busy'
import CbList from '@/shared/components/choice/CbList.vue'
import FormDialog from '@/shared/components/dialog/FormDialog.vue'
import FormField from '@/shared/components/form/FormField.vue'
import FormSection from '@/shared/components/form/FormSection.vue'
import DataTable from '@/shared/components/table/DataTable.vue'
import UserOption from '@/shared/components/UserOption.vue'
import { CrewDuty, RoleType } from '@/shared/constants/systemDicts'
import type { ShownError } from '@/shared/errors'
import { shownError } from '@/shared/errors'
import { formatDateTime } from '@/shared/format'
import { useLoad } from '@/shared/loadable'
import { roleReason } from '@/shared/roleReason'
import { tripRefText } from '@/shared/trips/tripRefText'
import { isMe, userName } from '@/shared/user'
import { staffColumns } from '../staff/staffColumns'
import CellCrewPick from './cells/CellCrewPick.vue'
import { CREW_PICK } from './crewPick'

/**
 * Диалог «Поездная бригада» (Ф-РЕЙ-02, Ф-РЕЙ-06): сверху — рейс и станция отправления, ниже — начальник поезда и ПЭМ
 * (назначает планировщик вагонов) и проводники станции отправления и уже назначенные в рейс (набирает планировщик
 * проводников); назначенные отмечены. Начальник поезда и ПЭМ — строками «Списка ФИО» (UserOption), проводники —
 * таблицей с колонками списка сотрудников (staffColumns) и отметкой у ФИО. Проводник, занятый в другом рейсе, показан,
 * если освободится к отправлению. Новые проводники попадают в
 * нераспределённые, снятые — уходят из бригады и с вагона. «Сохранить» записывает бригаду одной операцией.
 * Открывается через openDialog, результат — true.
 */
const props = defineProps<{ tripId: number; tripName: string; station: string; crew: CrewMember[] }>()

const show = defineModel<boolean>('show', { default: false })
// ожидание диалога: спиннер в подвале (FormDialog) — на всю цепочку загрузок формы
const busy = provideBusy()
const emit = defineEmits<{ done: [saved: boolean] }>()

const memberOf = (duty: number) => props.crew.find((m) => m.crewDutyType === duty)?.usr ?? null

const dutyReason = computed(() =>
  roleReason([RoleType.WAGON_PLANNER], 'Начальника поезда и ПЭМ назначает планировщик вагонов'),
)
const conductorReason = computed(() =>
  roleReason([RoleType.CREW_PLANNER], 'Проводников рейса набирает планировщик проводников'),
)

const lnp = ref<number | null>(memberOf(CrewDuty.LNP)?.id ?? null)
const pem = ref<number | null>(memberOf(CrewDuty.PEM)?.id ?? null)

// варианты начальника поезда и ПЭМ — свои выпадающие списки, форма их не ждёт; ошибка — в самом списке
const chiefs = shallowRef<StaffCandidate[]>([])
const pems = shallowRef<StaffCandidate[]>([])
const staffLoad = useLoad(async () => {
  const [a, b] = await Promise.all([
    fetchStaffCandidates(props.tripId, CrewDuty.LNP),
    fetchStaffCandidates(props.tripId, CrewDuty.PEM),
  ])
  chiefs.value = a
  pems.value = b
})
void staffLoad.run()

/** Занятость кандидата: «занят на рейсе … до …»; свободен — пусто. */
function staffBusy(c: StaffCandidate): string {
  if (!c.busyUntil) return ''
  return t('занят на рейсе {trip} до {until}', {
    trip: c.busyTrip ? tripRefText(c.busyTrip) : '',
    until: formatDateTime(c.busyUntil),
  })
}

const duties = computed(() => {
  const line = (duty: number, title: string, list: StaffCandidate[], model: typeof lnp) => {
    const member = memberOf(duty)
    const choices: { id: number; text: string; data?: Record<string, unknown> }[] = list.map((c) => ({
      id: c.id,
      text: userName(c),
      data: { busy: staffBusy(c) },
    }))
    // назначенный виден и если сейчас недоступен для выбора
    if (member && !choices.some((c) => c.id === member.id)) choices.unshift({ id: member.id, text: userName(member) })
    return { duty, title, choices, model }
  }
  return [line(CrewDuty.LNP, 'Начальник поезда', chiefs.value, lnp), line(CrewDuty.PEM, 'ПЭМ', pems.value, pem)]
})

const candidates = shallowRef<ConductorCandidate[]>([])
const selected = ref<number[]>([])
const loading = ref(true)
const running = ref(false)
// ошибка формы: проверки (formError) или запроса (shownError, запрос второй строкой); null — ошибки нет
const error = ref<ShownError | null>(null)

// загрузка формы — сразу в setup и в счёт ожидания диалога: спиннер в подвале с первой отрисовки
void busy.run(async () => {
  try {
    const [list, staff] = await Promise.all([
      fetchConductorCandidates(props.tripId),
      fetchStaff({ search: '', station: null, trip: null, train: null }, null),
    ])
    candidates.value = list
    staffRows.value = staff
    selected.value = list.filter((c) => c.assigned).map((c) => c.id)
  } catch (e) {
    error.value = shownError(e)
  } finally {
    loading.value = false
  }
})

function toggle(id: number) {
  selected.value = selected.value.includes(id) ? selected.value.filter((x) => x !== id) : [...selected.value, id]
}

// строки проводников — строки списка сотрудников (GET /staff) для кандидатов, в порядке кандидатов
const staffRows = shallowRef<StaffRow[]>([])
const conductorRows = computed(() => {
  const byId = new Map(staffRows.value.map((r) => [r.id, r]))
  return candidates.value.flatMap((c) => byId.get(c.id) ?? [])
})

// колонки списка сотрудников без сортировки: первая — ФИО с отметкой выбора
const conductorColumns = staffColumns().map((col) =>
  col.field === 'fullName' ? { ...col, sortable: false, cell: CellCrewPick } : { ...col, sortable: false },
)

provide(CREW_PICK, {
  isPicked: (id) => selected.value.includes(id),
  toggle,
  reason: () => conductorReason.value,
})

function onRowClick(row: StaffRow) {
  if (!conductorReason.value) toggle(row.id)
}

// пусто — с причиной: кандидаты — проводники депо станции отправления
const emptyText = computed(() =>
  props.station
    ? t('В депо станции {station} нет проводников', { station: props.station })
    : t('У маршрута рейса нет остановок: станция отправления не задана, проводников выбрать не из кого'),
)

async function save() {
  error.value = null
  running.value = true
  try {
    await setTripCrew(props.tripId, { lnp: lnp.value, pem: pem.value, conductors: selected.value })
    emit('done', true)
  } catch (e) {
    error.value = shownError(e)
  } finally {
    running.value = false
  }
}
</script>

<style scoped lang="scss">
// значение поля только для чтения — обычным текстом под подписью
.crew-pick__value {
  color: $zs-text;
  font-size: var(--zs-control-font-size);
}

// как подпись поля (FormField)
.crew-pick__caption {
  color: $zs-text-muted;
  font-size: 0.8rem;
  font-weight: 500;
}

// блок проводников занимает свободную высоту окна; подпись над таблицей — с отступом, как у поля (FormField)
.crew-pick__conductors {
  display: flex;
  flex: 1 1 0;
  flex-direction: column;
  gap: 0.4rem;
  min-height: 0;
}

// сколько назначено — бледным, обычным начертанием
.crew-pick__count {
  color: $zs-text-faint;
  font-weight: 400;
}

.crew-pick__table {
  flex: 1 1 0;
  min-height: 0;
}
</style>
