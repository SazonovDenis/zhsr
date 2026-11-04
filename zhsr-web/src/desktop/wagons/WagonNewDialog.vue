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
        <CbList
          v-model="draft.wagonType"
          single
          placeholder="Выберите…"
          :values="dictChoices('WagonType')"
          :source="dictSource('WagonType')"
        />
      </FormField>
      <FormField label="Модель">
        <CbList
          v-model="draft.modelType"
          single
          placeholder="Выберите…"
          :values="dictChoices('WagonModel')"
          :source="dictSource('WagonModel')"
        />
      </FormField>
      <FormField label="Год постройки">
        <TextInput v-model="draft.buildYear" inputmode="numeric" />
      </FormField>
      <FormField label="Изготовитель">
        <TextInput v-model="draft.maker" />
      </FormField>
    </FormSection>

    <FormSection title="Депо приписки" divided>
      <CbList
        v-model="draft.depot"
        single
        placeholder="Выберите…"
        :values="dictChoices('Depot')"
        :source="dictSource('Depot')"
      />
    </FormSection>

    <FormSection title="Начальное расположение и состояние" divided columns="repeat(2, minmax(0, 1fr))">
      <FormField label="Расположение" class="wagon-new__kind">
        <CbSegment
          v-model="draft.locationType"
          class="wagon-new__segment"
          single
          required
          :values="locationKinds"
          :max-button-count="3"
        />
      </FormField>
      <FormField :label="refLabel">
        <CbList
          v-model="draft.place"
          single
          placeholder="Выберите…"
          :values="refChoices"
          :source="refDict ? dictSource(refDict) : null"
        />
      </FormField>
      <FormField label="Состояние">
        <CbList v-model="draft.wagonStatusType" single placeholder="Выберите…" :values="initialStates" />
      </FormField>
    </FormSection>

    <FormSection divided columns="max-content">
      <FormField label="Ввод в эксплуатацию">
        <DatePicker
          v-model="draft.dbeg"
          date-text="Ввод в эксплуатацию"
          :quick="['today', 'yesterday', 'monthStart', 'yearStart']"
        />
      </FormField>
    </FormSection>
  </FormDialog>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'

import type { WagonRow, WagonDraft } from '@/api/wagons'
import { createWagon } from '@/api/wagons'
import { t } from '@/boot/i18n'
import type { ShownError } from '@/shared/errors'
import { formError, shownError } from '@/shared/errors'
import CbList from '@/shared/components/choice/CbList.vue'
import CbSegment from '@/shared/components/choice/CbSegment.vue'
import { provideBusy } from '@/shared/busy'
import FormDialog from '@/shared/components/dialog/FormDialog.vue'
import FormField from '@/shared/components/form/FormField.vue'
import DatePicker from '@/shared/components/period/DatePicker.vue'
import FormSection from '@/shared/components/form/FormSection.vue'
import TextInput from '@/shared/components/form/TextInput.vue'
import { dictChoices, dictSource } from '@/shared/dict/dictChoices'
import type { DictName } from '@/shared/dict/dicts'
import { LocationType, WagonStatus } from '@/shared/constants/systemDicts'

/**
 * Диалог «Ввод вагона»: паспорт, депо приписки, начальное расположение и состояние. Открывается через openDialog,
 * результат — созданный вагон.
 */

const show = defineModel<boolean>('show', { default: false })
// ожидание диалога: спиннер в подвале (FormDialog) — на всю цепочку загрузок формы
provideBusy()
const emit = defineEmits<{ done: [wagon: WagonRow] }>()

const draft = reactive<WagonDraft>({
  number: '',
  wagonType: null,
  modelType: null,
  buildYear: '',
  maker: '',
  depot: null,
  locationType: LocationType.DEPOT,
  place: null,
  wagonStatusType: null,
  dbeg: null,
})

const running = ref(false)
// ошибка формы: проверки (formError) или запроса (shownError, запрос второй строкой); null — ошибки нет
const error = ref<ShownError | null>(null)

// вагон вводится в депо, в парк отстоя или на станцию; «в рейсе» он оказывается только при формировании рейса
const locationKinds = computed(() => dictChoices('LocationKind').filter((c) => c.id !== LocationType.TRIP))

// откуда выбирается место — по виду расположения
const refDicts: Record<number, { dict: DictName; label: string }> = {
  [LocationType.DEPOT]: { dict: 'Depot', label: 'Депо' },
  [LocationType.YARD]: { dict: 'Yard', label: 'Парк отстоя' },
  [LocationType.STATION]: { dict: 'Station', label: 'Станция' },
}
const refLabel = computed(() => refDicts[draft.locationType]?.label ?? '')
const refDict = computed(() => refDicts[draft.locationType]?.dict ?? null)
const refChoices = computed(() => (refDict.value ? dictChoices(refDict.value) : []))
watch(
  () => draft.locationType,
  () => (draft.place = null),
)

// «В пути» вагон получает только в рейсе, поэтому начальным состоянием быть не может
const initialStates = computed(() => dictChoices('WagonState').filter((c) => c.id !== WagonStatus.IN_TRIP))

// справочники формы контролы загружают сами (dictSource): спиннер и ошибка — в выпадающем списке

function validate(): string {
  if (!/^\d{5,8}$/.test(draft.number)) return t('Номер вагона — от 5 до 8 цифр')
  if (!draft.wagonType || !draft.modelType || !draft.depot || !draft.place || !draft.wagonStatusType || !draft.dbeg) {
    return t('Заполните все поля')
  }
  return ''
}

async function save() {
  const invalid = validate()
  error.value = invalid ? formError(invalid) : null
  if (error.value) return
  running.value = true
  try {
    emit('done', await createWagon({ ...draft }))
  } catch (e) {
    error.value = shownError(e)
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
