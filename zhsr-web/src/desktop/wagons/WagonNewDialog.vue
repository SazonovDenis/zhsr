<template>
  <FormDialog
    v-model:show="show"
    title="Ввод вагона"
    ok-text="Ввести вагон"
    :running="running"
    :error="error"
    @ok="save"
  >
    <FormSection title="Паспорт" columns="repeat(2, minmax(0, 1fr))">
      <FormField label="Номер вагона">
        <TextInput v-model="draft.number" mono inputmode="numeric" placeholder="5–8 цифр" />
      </FormField>
      <FormField label="Тип вагона">
        <CbList v-model="draft.type" single placeholder="Выберите…" :values="dictChoices('WagonType')" />
      </FormField>
      <FormField label="Модель">
        <CbList v-model="draft.model" single placeholder="Выберите…" :values="dictChoices('WagonModel')" />
      </FormField>
      <FormField label="Год постройки">
        <TextInput v-model="draft.year" inputmode="numeric" />
      </FormField>
      <FormField label="Изготовитель">
        <TextInput v-model="draft.maker" />
      </FormField>
    </FormSection>

    <FormSection title="Депо приписки" divided>
      <CbList v-model="draft.depot" single placeholder="Выберите…" :values="dictChoices('Depot')" />
    </FormSection>

    <FormSection title="Начальное расположение и состояние" divided columns="repeat(2, minmax(0, 1fr))">
      <FormField label="Расположение" class="wagon-new__kind">
        <CbSegment
          v-model="draft.locationKind"
          class="wagon-new__segment"
          single
          required
          :values="locationKinds"
          :max-button-count="3"
        />
      </FormField>
      <FormField :label="refLabel">
        <CbList v-model="draft.locationRef" single placeholder="Выберите…" :values="refChoices" />
      </FormField>
      <FormField label="Состояние">
        <CbList v-model="draft.state" single placeholder="Выберите…" :values="initialStates" />
      </FormField>
    </FormSection>

    <FormSection divided columns="max-content">
      <FormField label="Ввод в эксплуатацию">
        <DatePicker
          v-model="draft.since"
          date-text="Ввод в эксплуатацию"
          :quick="['today', 'yesterday', 'monthStart', 'yearStart']"
        />
      </FormField>
    </FormSection>
  </FormDialog>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'

import type { Wagon, WagonDraft } from '@/api/wagons'
import { createWagon } from '@/api/wagons'
import { t } from '@/boot/i18n'
import CbList from '@/shared/components/choice/CbList.vue'
import CbSegment from '@/shared/components/choice/CbSegment.vue'
import FormDialog from '@/shared/components/dialog/FormDialog.vue'
import FormField from '@/shared/components/form/FormField.vue'
import DatePicker from '@/shared/components/period/DatePicker.vue'
import FormSection from '@/shared/components/form/FormSection.vue'
import TextInput from '@/shared/components/form/TextInput.vue'
import { dictChoices } from '@/shared/dict/dictChoices'
import { dictManager } from '@/shared/dict/DictManager'
import type { DictName } from '@/shared/dict/dicts'

/**
 * Диалог «Ввод вагона»: паспорт, депо приписки, начальное расположение и состояние. Открывается через openDialog,
 * результат — созданный вагон.
 */

const show = defineModel<boolean>('show', { default: false })
const emit = defineEmits<{ done: [wagon: Wagon] }>()

const draft = reactive<WagonDraft>({
  number: '',
  type: '',
  model: '',
  year: '',
  maker: '',
  depot: '',
  locationKind: 'DEPOT',
  locationRef: '',
  state: '',
  since: null,
})

const running = ref(false)
const error = ref('')

// вагон вводится в депо, в парк отстоя или на станцию; «в рейсе» он оказывается только при формировании рейса
const locationKinds = computed(() => dictChoices('LocationKind').filter((c) => c.id !== 'TRIP'))

// откуда выбирается место — по виду расположения
const refDicts: Record<string, { dict: DictName; label: string }> = {
  DEPOT: { dict: 'Depot', label: 'Депо' },
  YARD: { dict: 'Yard', label: 'Парк отстоя' },
  STATION: { dict: 'Station', label: 'Станция' },
}
const refLabel = computed(() => refDicts[draft.locationKind]?.label ?? '')
const refChoices = computed(() => {
  const ref = refDicts[draft.locationKind]
  return ref ? dictChoices(ref.dict) : []
})
watch(
  () => draft.locationKind,
  () => (draft.locationRef = ''),
)

// «В пути» вагон получает только в рейсе, поэтому начальным состоянием быть не может
const initialStates = computed(() => dictChoices('WagonState').filter((c) => c.id !== 'IN_TRIP'))

onMounted(() =>
  dictManager.prepareDicts('WagonType', 'WagonModel', 'Depot', 'Yard', 'Station', 'LocationKind', 'WagonState'),
)

function validate(): string {
  if (!/^\d{5,8}$/.test(draft.number)) return t('Номер вагона — от 5 до 8 цифр')
  if (!draft.type || !draft.model || !draft.depot || !draft.locationRef || !draft.state || !draft.since) {
    return t('Заполните все поля')
  }
  return ''
}

async function save() {
  error.value = validate()
  if (error.value) return
  running.value = true
  try {
    emit('done', await createWagon({ ...draft }))
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    running.value = false
  }
}
</script>

<style scoped lang="scss">
.wagon-new__kind {
  grid-column: 1 / -1;
}

.wagon-new__segment {
  align-self: flex-start;
}
</style>
