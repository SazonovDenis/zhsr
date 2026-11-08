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
      <WagonPickField v-model="wagonNumber" @pick="onWagon" />
    </FormSection>

    <FormSection title="Неисправность" divided>
      <CbList
        v-model="draft.faultType"
        single
        placeholder="Поиск словами: «окно дует»"
        :values="faultChoices"
        :source="dictSource('FaultType')"
        @update:model-value="onFault"
      >
        <template #option="{ item }">
          <div class="request-dialog__fault">
            <span>{{ item.text }}</span>
            <span class="zs-sub">{{ item.data?.group }}</span>
          </div>
        </template>
      </CbList>
      <div class="request-dialog__frequent">
        <span class="request-dialog__frequent-label q-mr-xs">{{ t('Частые') }}</span>
        <button
          v-for="f in frequent"
          :key="f.id"
          type="button"
          class="request-dialog__chip zs-focusable q-py-none q-px-sm"
          :class="{ 'request-dialog__chip--on bg-selected text-selected': draft.faultType === f.id }"
          @click="pickFault(f.id)"
        >
          {{ f.name }}
        </button>
      </div>
    </FormSection>

    <FormSection divided columns="repeat(2, minmax(0, 1fr))">
      <FormField label="Критичность" class="request-dialog__wide">
        <CbSegment v-model="draft.severityType" single required :values="severities" :max-button-count="4" />
      </FormField>
      <FormField label="Служба">
        <span>{{ serviceName }}</span>
      </FormField>
      <FormField label="Рейс">
        <CbList v-model="draft.trip" single placeholder="Без рейса" :values="tripChoices" :source="tripLoad" />
      </FormField>
      <FormField v-if="canChooseExecutor" label="Исполнитель" class="request-dialog__wide">
        <CbList
          v-model="draft.executorUsr"
          single
          placeholder="Не назначать — возьмёт любой сотрудник службы"
          :values="staffChoices"
        />
      </FormField>
    </FormSection>

    <FormSection divided>
      <FormField label="Описание">
        <textarea
          v-model="draft.description"
          class="request-dialog__textarea zs-focusable q-pa-sm"
          rows="3"
          :placeholder="t('Например: в 3-м купе не закрывается окно')"
        />
      </FormField>
      <FormField :label="request ? 'Добавить фото' : 'Фото'">
        <PhotoPicker v-model="draft.photos" />
      </FormField>
    </FormSection>
  </FormDialog>
</template>

<script setup lang="ts">
import { computed, reactive, ref, shallowRef, watch } from 'vue'

import type { RepairRequest, RequestDraft, UserRef } from '@/api/requests'
import { createRequest, fetchServiceStaff, updateRequest } from '@/api/requests'
import type { TripOption } from '@/api/trips'
import { fetchTripOptions } from '@/api/trips'
import type { RequestWagon } from '@/api/wagons'
import { t } from '@/boot/i18n'
import type { ShownError } from '@/shared/errors'
import { formError, shownError } from '@/shared/errors'
import CbList from '@/shared/components/choice/CbList.vue'
import CbSegment from '@/shared/components/choice/CbSegment.vue'
import type { ChoiceItem, ChoiceModel } from '@/shared/components/choice/choice'
import { provideBusy } from '@/shared/busy'
import FormDialog from '@/shared/components/dialog/FormDialog.vue'
import FormField from '@/shared/components/form/FormField.vue'
import FormSection from '@/shared/components/form/FormSection.vue'
import { dictChoices, dictSource } from '@/shared/dict/dictChoices'
import { Severity, TripStatus } from '@/shared/constants/systemDicts'
import { dictManager } from '@/shared/dict/DictManager'
import { useLoad } from '@/shared/loadable'
import { faultDefaults, frequentFaults } from '@/shared/requests/faults'
import PhotoPicker from '@/shared/requests/PhotoPicker.vue'
import { canAssignOnCreate } from '@/shared/requests/rights'
import { currentUser, loadCurrentUser, userName } from '@/shared/user'
import WagonPickField from './WagonPickField.vue'
import { tripRefText } from '@/shared/trips/tripRefText'

/**
 * Диалог «Новая заявка» (Ф-ЗАЯ-01) и «Исправление заявки» (Ф-ЗАЯ-07, задан request): вагон, неисправность,
 * критичность, рейс, описание и фото. Критичность подставляется из неисправности, служба определяется ею, рейс — из вагона;
 * все поля можно изменить. Мастер депо при подаче может сразу назначить исполнителя. Открывается через openDialog,
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
// номер вагона в поле выбора; вагон заявки (draft.wagon) — выбранный из списка: номер исправлен вручную — не выбран
const wagonNumber = ref(r?.wagon.number ?? '')
let pickedNumber = wagonNumber.value
watch(wagonNumber, (number) => {
  if (number !== pickedNumber) draft.wagon = null
})

const running = ref(false)
// ошибка формы: проверки (formError) или запроса (shownError, запрос второй строкой); null — ошибки нет
const error = ref<ShownError | null>(null)

// неисправности: все, поиск — и по названию группы; в строке списка группа второй строкой
const faultChoices = computed<ChoiceItem[]>(() =>
  dictManager
    .getList('FaultType')
    .filter((f) => f.disabled !== true)
    .map((f) => {
      const group = dictManager.getValue('FaultGroup', f.faultGroupType as number | null)
      return { id: f.id, text: dictManager.getValue('FaultType', f.id), search: group, data: { group } }
    }),
)
const frequent = computed(() => frequentFaults())
const severities = computed(() => [...dictChoices('Severity')].reverse())
// служба определяется неисправностью
const service = computed(() => (draft.faultType ? faultDefaults(draft.faultType).service : null))
const serviceName = computed(() => (service.value ? dictManager.getValue('Service', service.value) : ''))

// рейсы: идущие и планируемые; рейс заявки есть в списке, даже если он уже закончился
const trips = shallowRef<TripOption[]>([])
const tripChoices = computed<ChoiceItem[]>(() => {
  const list: ChoiceItem[] = trips.value.map((trip) => ({ id: trip.id, text: `${trip.trainNumber} • ${trip.name}` }))
  if (r?.trip && !list.some((c) => c.id === r.trip!.id)) list.unshift({ id: r.trip.id, text: tripRefText(r.trip) })
  return list
})

const canChooseExecutor = computed(() => !r && !!currentUser.value && canAssignOnCreate(currentUser.value))
const staff = shallowRef<UserRef[]>([])
const staffChoices = computed(() => staff.value.map((u) => ({ id: u.id, text: userName(u) })))

watch(
  () => (canChooseExecutor.value ? service.value : null),
  async (id) => {
    draft.executorUsr = null
    staff.value = id ? await fetchServiceStaff(id) : []
  },
)

const hint = computed(() =>
  r ? 'Исправить заявку можно в течение 10 минут после подачи, пока не сменились исполнитель и статус' : '',
)

function onWagon(w: RequestWagon) {
  pickedNumber = w.number
  draft.wagon = w.id
  draft.trip = w.trip?.id ?? null
}

// неисправность выбрана — критичность по умолчанию из неё
function onFault(value: ChoiceModel) {
  if (typeof value !== 'number') return
  draft.severityType = faultDefaults(value).severity
}

function pickFault(id: number) {
  draft.faultType = id
  onFault(id)
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

// рейсы — варианты выпадающего списка «Рейс»: своя загрузка, форма её не ждёт; ошибка — в самом списке
const tripLoad = useLoad(async () => {
  trips.value = await fetchTripOptions({
    route: null,
    station: null,
    from: null,
    to: null,
    states: [TripStatus.IN_PROGRESS, TripStatus.PLANNED],
  })
})
void tripLoad.run()

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

.request-dialog__fault {
  display: flex;
  flex-direction: column;
  max-width: 30rem;
}

.request-dialog__frequent {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.4rem;
}

.request-dialog__frequent-label {
  color: $zs-text-muted;
  font-size: 0.8rem;
}

.request-dialog__chip {
  height: 1.9rem;
  border: 0.0625rem solid $zs-border-input;
  border-radius: 0.9rem;
  background: #fff;
  color: $zs-text;
  font: inherit;
  font-size: 0.8rem;
  cursor: pointer;

  &:hover:not(.request-dialog__chip--on) {
    background: $zs-bg-hover;
  }

  &--on {
    border-color: $zs-selected-border;
  }
}

.request-dialog__textarea {
  border: 0.0625rem solid $zs-border-input;
  border-radius: 0.3rem;
  color: $zs-text;
  font: inherit;
  font-size: 0.9rem;
  resize: vertical;

  &:focus {
    border-color: $primary;
    outline: none;
    box-shadow: 0 0 0 0.0625rem $primary;
  }
}
</style>
