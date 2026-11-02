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
      <WagonPickField v-model="draft.wagon" @pick="onWagon" />
    </FormSection>

    <FormSection title="Неисправность" divided>
      <CbList
        v-model="draft.fault"
        single
        placeholder="Поиск словами: «окно дует»"
        :values="faultChoices"
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
        <span class="request-dialog__frequent-label">{{ t('Частые') }}</span>
        <button
          v-for="f in frequent"
          :key="f.id"
          type="button"
          class="request-dialog__chip zs-focusable"
          :class="{ 'request-dialog__chip--on bg-selected text-selected': draft.fault === f.id }"
          @click="pickFault(f.id)"
        >
          {{ f.name }}
        </button>
      </div>
    </FormSection>

    <FormSection divided columns="repeat(2, minmax(0, 1fr))">
      <FormField label="Критичность" class="request-dialog__wide">
        <CbSegment v-model="draft.severity" single required :values="severities" :max-button-count="4" />
      </FormField>
      <FormField label="Служба">
        <CbList v-model="draft.service" single no-search :values="services" />
      </FormField>
      <FormField label="Рейс">
        <CbList v-model="draft.trip" single placeholder="Без рейса" :values="tripChoices" />
      </FormField>
      <FormField v-if="canChooseExecutor" label="Исполнитель" class="request-dialog__wide">
        <CbList
          v-model="draft.executor"
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
          class="request-dialog__textarea zs-focusable"
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
import { computed, onMounted, reactive, ref, shallowRef, watch } from 'vue'

import type { RepairRequest, RequestDraft, UserRef } from '@/api/requests'
import { createRequest, fetchServiceStaff, updateRequest } from '@/api/requests'
import type { Trip } from '@/api/trips'
import { fetchTrips } from '@/api/trips'
import type { RequestWagon } from '@/api/wagons'
import { t } from '@/boot/i18n'
import CbList from '@/shared/components/choice/CbList.vue'
import CbSegment from '@/shared/components/choice/CbSegment.vue'
import type { ChoiceItem, ChoiceModel } from '@/shared/components/choice/choice'
import FormDialog from '@/shared/components/dialog/FormDialog.vue'
import FormField from '@/shared/components/form/FormField.vue'
import FormSection from '@/shared/components/form/FormSection.vue'
import { dictChoices } from '@/shared/dict/dictChoices'
import { dictManager } from '@/shared/dict/DictManager'
import { faultDefaults, frequentFaults } from '@/shared/requests/faults'
import PhotoPicker from '@/shared/requests/PhotoPicker.vue'
import { REQUEST_DICTS } from '@/shared/requests/requestView'
import { canAssignOnCreate } from '@/shared/requests/rights'
import { currentUser, loadCurrentUser } from '@/shared/user'
import WagonPickField from './WagonPickField.vue'

/**
 * Диалог «Новая заявка» (Ф-ЗАЯ-01) и «Исправление заявки» (Ф-ЗАЯ-07, задан request): вагон, неисправность,
 * критичность, служба, рейс, описание и фото. Критичность и служба подставляются из неисправности, рейс — из вагона;
 * все поля можно изменить. Мастер депо при подаче может сразу назначить исполнителя. Открывается через openDialog,
 * результат — поданная или исправленная заявка.
 */
const props = withDefaults(defineProps<{ request?: RepairRequest | null }>(), { request: null })

const show = defineModel<boolean>('show', { default: false })
const emit = defineEmits<{ done: [request: RepairRequest] }>()

const r = props.request
const draft = reactive<RequestDraft>({
  wagon: r?.wagon ?? '',
  trip: r?.trip?.id ?? null,
  fault: r?.fault ?? '',
  service: r?.service ?? '',
  severity: r?.severity ?? 'MEDIUM',
  description: r?.description ?? '',
  photos: [],
  executor: null,
})

const running = ref(false)
const error = ref('')

// неисправности: все, поиск — и по названию группы; в строке списка группа второй строкой
const faultChoices = computed<ChoiceItem[]>(() =>
  dictManager.getList('FaultType').map((f) => {
    const group = dictManager.getValue('FaultGroup', f.group as string)
    return { id: f.id, text: dictManager.getValue('FaultType', f.id), search: group, data: { group } }
  }),
)
const frequent = computed(() => frequentFaults())
const severities = computed(() => [...dictChoices('Severity')].reverse())
const services = computed(() =>
  dictChoices('Service').filter((c) => ['TECHNICAL', 'CLEANING', 'EQUIPMENT'].includes(String(c.id))),
)

// рейсы: идущие и планируемые; рейс заявки есть в списке, даже если он уже закончился
const trips = shallowRef<Trip[]>([])
const tripChoices = computed<ChoiceItem[]>(() => {
  const list: ChoiceItem[] = trips.value.map((trip) => ({ id: trip.id, text: `${trip.train} ${trip.routeName}` }))
  if (r?.trip && !list.some((c) => c.id === r.trip!.id)) list.unshift({ id: r.trip.id, text: r.trip.name })
  return list
})

const canChooseExecutor = computed(() => !r && !!currentUser.value && canAssignOnCreate(currentUser.value))
const staff = shallowRef<UserRef[]>([])
const staffChoices = computed(() => staff.value.map((u) => ({ id: u.id, text: u.name })))

watch(
  () => (canChooseExecutor.value ? draft.service : ''),
  async (service) => {
    draft.executor = null
    staff.value = service ? await fetchServiceStaff(service) : []
  },
)

const hint = computed(() =>
  r ? 'Исправить заявку можно в течение 10 минут после подачи, пока не сменились исполнитель и статус' : '',
)

function onWagon(w: RequestWagon) {
  draft.trip = w.trip?.id ?? null
}

// неисправность выбрана — критичность и служба по умолчанию из неё
function onFault(value: ChoiceModel) {
  if (typeof value !== 'string' || !value) return
  const d = faultDefaults(value)
  draft.severity = d.severity
  draft.service = d.service
}

function pickFault(id: string) {
  draft.fault = id
  onFault(id)
}

onMounted(async () => {
  await Promise.all([dictManager.prepareDicts(...REQUEST_DICTS), loadCurrentUser()])
  trips.value = await fetchTrips(
    { route: null, station: null, from: null, to: null, states: ['IN_PROGRESS', 'PLANNED'] },
    null,
  )
})

async function save() {
  if (!draft.wagon || !draft.fault || !draft.service || !draft.severity) {
    error.value = t('Выберите вагон и неисправность')
    return
  }
  error.value = ''
  running.value = true
  try {
    const data = { ...draft, photos: [...draft.photos] }
    emit('done', r ? await updateRequest(r.id, data) : await createRequest(data))
  } catch (e) {
    error.value = e instanceof Error ? t(e.message) : String(e)
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
  gap: 0.375rem;
}

.request-dialog__frequent-label {
  margin-right: 0.25rem;
  color: $zs-text-muted;
  font-size: 0.75rem;
}

.request-dialog__chip {
  height: 1.9rem;
  padding: 0 0.625rem;
  border: 0.0625rem solid $zs-border-input;
  border-radius: 0.9375rem;
  background: #fff;
  color: $zs-text;
  font: inherit;
  font-size: 0.8125rem;
  cursor: pointer;

  &:hover:not(.request-dialog__chip--on) {
    background: $zs-bg-hover;
  }

  &--on {
    border-color: $zs-selected-border;
  }
}

.request-dialog__textarea {
  padding: 0.5rem 0.625rem;
  border: 0.0625rem solid $zs-border-input;
  border-radius: 0.25rem;
  color: $zs-text;
  font: inherit;
  font-size: 0.875rem;
  resize: vertical;

  &:focus {
    border-color: $primary;
    outline: none;
    box-shadow: 0 0 0 0.0625rem $primary;
  }
}
</style>
