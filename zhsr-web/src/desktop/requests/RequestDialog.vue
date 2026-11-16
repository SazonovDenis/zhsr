<template>
  <FormDialog
    v-model:show="show"
    :title="request ? 'Исправление заявки' : 'Новая заявка'"
    :ok-text="request ? 'Сохранить' : 'Отправить'"
    width="46rem"
    :hint="hint"
    :running="running"
    :error="error"
    @ok="save"
  >
    <FormSection title="Вагон">
      <RequestWagonField v-model="draft.wagon" :initial="r?.wagon ?? null" @pick="onWagon" />
    </FormSection>

    <FormSection title="Неисправность" divided>
      <FaultPicker v-model="draft.faultType" @update:model-value="onFault" />
    </FormSection>

    <FormSection divided columns="repeat(2, minmax(0, 1fr))">
      <FormField label="Критичность" class="request-dialog__wide">
        <CbSegment v-model="draft.severityType" single required :values="severities" :max-button-count="4" />
      </FormField>
      <!-- поле есть всегда, если пользователь вообще назначает при подаче: иначе оно появлялось бы и пропадало по
           выбору вагона, и форма дёргалась; назначить сейчас нельзя — поле недоступно, причина во всплывающей подсказке -->
      <FormField v-if="executorShown" label="Исполнитель" class="request-dialog__wide">
        <CbList
          v-model="draft.executorUsr"
          single
          placeholder="Не назначать — возьмёт любой сотрудник службы"
          :values="staffChoices"
          :disabled="!canChooseExecutor"
          :title="canChooseExecutor ? undefined : t('Назначить можно, если выбран вагон вашего рейса')"
        >
          <template #option="{ item }">
            <StaffOption
              :name="item.text ?? ''"
              :candidate="(item.data?.candidate as StaffCandidate) ?? null"
              :wagon-number="wagonNumber || null"
            />
          </template>
        </CbList>
      </FormField>
    </FormSection>

    <FormSection divided>
      <FormField label="Описание">
        <NoteInput v-model="draft.description" placeholder="Например: в 3-м купе не закрывается окно" />
      </FormField>
      <FormField :label="request ? 'Добавить фото' : 'Фото'">
        <PhotoPicker v-model="draft.photos" />
      </FormField>
    </FormSection>
  </FormDialog>
</template>

<script setup lang="ts">
import { computed, reactive, ref, shallowRef, watch } from 'vue'

import type { RepairRequest, RequestDraft, StaffCandidate } from '@/api/requests'
import { createRequest, fetchServiceStaff, updateRequest } from '@/api/requests'
import type { RequestWagon } from '@/api/wagons'
import { t } from '@/boot/i18n'
import type { ShownError } from '@/shared/errors'
import { formError, shownError } from '@/shared/errors'
import CbList from '@/shared/components/choice/CbList.vue'
import CbSegment from '@/shared/components/choice/CbSegment.vue'
import type { ChoiceModel } from '@/shared/components/choice/choice'
import { provideBusy } from '@/shared/busy'
import FormDialog from '@/shared/components/dialog/FormDialog.vue'
import NoteInput from '@/shared/components/form/NoteInput.vue'
import FormField from '@/shared/components/form/FormField.vue'
import FormSection from '@/shared/components/form/FormSection.vue'
import { dictChoices } from '@/shared/dict/dictChoices'
import { CrewDuty, RoleType, Severity } from '@/shared/constants/systemDicts'
import { dictManager } from '@/shared/dict/DictManager'
import { faultDefaults } from '@/shared/requests/faults'
import FaultPicker from '@/shared/requests/FaultPicker.vue'
import PhotoPicker from '@/shared/requests/PhotoPicker.vue'
import { canAssignOnCreate, staffTrip } from '@/shared/requests/rights'
import { currentUser, hasRole, loadCurrentUser, userName } from '@/shared/user'
import StaffOption from '@/shared/requests/StaffOption.vue'
import { sortStaff } from '@/shared/requests/staff'
import RequestWagonField from './RequestWagonField.vue'

/**
 * Диалог «Новая заявка» (Ф-ЗАЯ-01) и «Исправление заявки» (Ф-ЗАЯ-07, задан request): вагон, неисправность,
 * критичность, описание и фото. Критичность подставляется из неисправности и её можно изменить; служба определяется
 * неисправностью, рейс — вагоном: в форме их нет. Без смены вагона рейс исправляемой заявки остаётся прежним. Мастер депо при подаче может сразу назначить исполнителя. Открывается через openDialog,
 * результат — поданная или исправленная заявка.
 */
const props = withDefaults(defineProps<{ request?: RepairRequest | null }>(), { request: null })

const show = defineModel<boolean>('show', { default: false })
// ожидание диалога: спиннер в подвале (FormDialog) — на всю цепочку загрузок формы
const busy = provideBusy()
const emit = defineEmits<{ done: [request: RepairRequest] }>()

const r = props.request
// вагон и неисправность не выбраны — null; проверяются при сохранении
const draft = reactive<
  Omit<RequestDraft, 'wagon' | 'faultType'> & {
    wagon: number | null
    faultType: number | null
  }
>({
  wagon: r?.wagon.id ?? null,
  trip: r?.trip?.id ?? null,
  faultType: r?.faultType ?? null,
  severityType: r?.severityType ?? Severity.MEDIUM,
  description: r?.description ?? '',
  photos: [],
  executorUsr: null,
})

const running = ref(false)
// ошибка формы: проверки (formError) или запроса (shownError, запрос второй строкой); null — ошибки нет
const error = ref<ShownError | null>(null)

const severities = computed(() => [...dictChoices('Severity')].reverse())
// служба определяется неисправностью
const service = computed(() => (draft.faultType ? faultDefaults(draft.faultType).service : null))

// рейс выбранного вагона: ЛНП выбирает исполнителя по вагону своего рейса
const wagonTrip = ref<number | null>(null)
const canChooseExecutor = computed(
  () => !r && !!currentUser.value && canAssignOnCreate(currentUser.value, draft.wagon ? wagonTrip.value : null),
)
// поле исполнителя: мастер депо и администратор назначают всегда, ЛНП — только по вагону своего рейса
const executorShown = computed(() => {
  const u = currentUser.value
  if (r || !u) return false
  return hasRole(u, RoleType.MASTER) || hasRole(u, RoleType.ADMIN) || u.trip?.crewDutyType === CrewDuty.LNP
})
const staff = shallowRef<StaffCandidate[]>([])
// номер выбранного вагона: у кого из бригады этот вагон — жирным и выше в списке
const wagonNumber = ref(r?.wagon.number ?? '')
// у сотрудника бригады рейса справа — должность и вагоны (StaffOption); предпочтительные — первыми
const staffChoices = computed(() =>
  sortStaff(staff.value, wagonNumber.value || null).map((c) => ({
    id: c.usr.id,
    text: userName(c.usr),
    data: { candidate: c },
  })),
)

// ЛНП выбирает исполнителя из бригады рейса вагона, мастер депо — из всех сотрудников службы
watch(
  // источники по отдельности: watch сравнивает каждый, а не новый массив — иначе любой пересчёт стирал исполнителя
  [() => (canChooseExecutor.value ? service.value : null), wagonTrip],
  async ([id, trip]) => {
    draft.executorUsr = null
    staff.value = id && currentUser.value ? await fetchServiceStaff(id, staffTrip(currentUser.value, trip)) : []
  },
)

const hint = computed(() =>
  r ? 'Исправить заявку можно в течение 10 минут после подачи, пока не сменились исполнитель и статус' : '',
)

function onWagon(w: RequestWagon) {
  wagonNumber.value = w.number
  wagonTrip.value = w.trip?.id ?? null
  // рейс заявки — рейс, в составе которого вагон сейчас
  draft.trip = w.trip?.id ?? null
}

// неисправность выбрана — критичность по умолчанию из неё
function onFault(value: ChoiceModel) {
  if (typeof value !== 'number') return
  draft.severityType = faultDefaults(value).severity
}

// ошибка подготовки формы — в поле ошибки диалога
// загрузка формы — сразу в setup и в счёт ожидания диалога: спиннер в подвале с первой отрисовки
// пользователь (от него зависит, можно ли выбрать исполнителя) и справочники неисправностей для частых неисправностей
// и групп в списке неисправностей — параллельно; остальные справочники формы контролы загружают сами (dictSource)
void busy.run(async () => {
  try {
    await Promise.all([loadCurrentUser(), dictManager.prepareDicts('FaultGroup', 'FaultType')])
  } catch (e) {
    error.value = shownError(e)
  }
})

async function save() {
  if (!draft.wagon || !draft.faultType || !draft.severityType) {
    error.value = formError(t('Выберите вагон и неисправность'))
    return
  }
  error.value = null
  running.value = true
  try {
    const data: RequestDraft = {
      ...draft,
      wagon: draft.wagon,
      faultType: draft.faultType,
      photos: [...draft.photos],
    }
    emit('done', r ? await updateRequest(r.id, data) : await createRequest(data))
  } catch (e) {
    error.value = shownError(e)
  } finally {
    running.value = false
  }
}
</script>

<style scoped lang="scss">
.request-dialog__wide {
  grid-column: 1 / -1;
}
</style>
