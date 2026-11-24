<template>
  <FormDialog
    v-model:show="show"
    min-height="lg"
    :title="title"
    :ok-text="okText"
    :width="width"
    :hint="hint"
    :running="running"
    :error="error"
    @ok="onSave"
  >
    <FormSection columns="repeat(2, minmax(0, 1fr))">
      <template v-for="e in shown" :key="e.f.name">
        <FormField :label="e.f.required ? `${t(e.f.label)} *` : e.f.label" :class="{ 'spec-dialog__wide': e.f.wide }">
          <CbSegment v-if="e.f.kind === 'bool'" v-model="e.choice" single required :values="yesNo" />
          <CbList
            v-else-if="e.f.kind === 'single'"
            v-model="e.choice"
            single
            :placeholder="e.f.placeholder ?? 'Нет'"
            :values="e.f.choices ?? []"
            :source="e.f.source ?? null"
          />
          <FaultPicker
            v-else-if="e.f.kind === 'fault'"
            :model-value="typeof e.choice === 'number' ? e.choice : null"
            :placeholder="e.f.placeholder ?? 'Поиск словами: «окно дует»'"
            @update:model-value="e.choice = $event"
          />
          <CbList
            v-else-if="e.f.kind === 'multi'"
            v-model="e.choices"
            :placeholder="e.f.placeholder ?? 'Выберите…'"
            :values="e.f.choices ?? []"
            :source="e.f.source ?? null"
          />
          <TextInput
            v-else
            v-model="e.text"
            :inputmode="e.f.kind === 'number' ? 'decimal' : 'text'"
            :placeholder="e.f.placeholder ?? ''"
          />
        </FormField>
      </template>
    </FormSection>

    <template #actions-left>
      <q-btn
        v-if="remove"
        no-caps
        unelevated
        outline
        color="negative"
        :label="confirming ? t('Нажмите ещё раз, чтобы удалить') : t('Удалить')"
        :disable="running"
        class="zs-focusable"
        @click="onRemove"
      />
    </template>
  </FormDialog>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'

import { t } from '@/boot/i18n'
import { provideBusy } from '@/shared/busy'
import type { ChoiceId, ChoiceItem, ChoiceModel } from '@/shared/components/choice/choice'
import CbList from '@/shared/components/choice/CbList.vue'
import CbSegment from '@/shared/components/choice/CbSegment.vue'
import FormDialog from '@/shared/components/dialog/FormDialog.vue'
import FormField from '@/shared/components/form/FormField.vue'
import FormSection from '@/shared/components/form/FormSection.vue'
import TextInput from '@/shared/components/form/TextInput.vue'
import FaultPicker from '@/shared/requests/FaultPicker.vue'
import type { ShownError } from '@/shared/errors'
import { formError, shownError } from '@/shared/errors'
import type { SpecField, SpecValues } from './spec'

/**
 * Диалог записи администрирования по описанию полей (spec.ts): текст, число, «да/нет», выбор одного или нескольких
 * значений. Поле может зависеть от других полей (visible). Сохраняет и удаляет вызывающий (save, remove); «Удалить» —
 * со вторым кликом для подтверждения. Результат (событие done): ответ save или 'deleted'.
 */
const props = withDefaults(
  defineProps<{
    title: string
    okText?: string
    width?: string
    hint?: string
    fields: SpecField[]
    initial: SpecValues
    save: (values: SpecValues) => Promise<unknown>
    remove?: (() => Promise<void>) | null
  }>(),
  { okText: 'Сохранить', width: '40rem', hint: '', remove: null },
)

const show = defineModel<boolean>('show', { default: false })
// ожидание диалога: спиннер в подвале (FormDialog)
provideBusy()
const emit = defineEmits<{ done: [result: unknown] }>()

const yesNo: ChoiceItem[] = [
  { id: 'yes', text: 'Да' },
  { id: 'no', text: 'Нет' },
]

/** Поле и его значение по виду: текст и число — text строкой, «да/нет» и одно значение — choice, несколько — choices. */
interface Entry {
  f: SpecField
  text: string
  choice: ChoiceModel
  choices: ChoiceModel
}

const entries = reactive<Entry[]>(
  props.fields.map((f) => {
    const v = props.initial[f.name]
    return {
      f,
      text:
        (f.kind === 'text' || f.kind === 'number') && (typeof v === 'string' || typeof v === 'number') ? String(v) : '',
      choice:
        f.kind === 'bool' ? (v === true ? 'yes' : 'no') : typeof v === 'number' || typeof v === 'string' ? v : null,
      choices: Array.isArray(v) ? [...(v as ChoiceId[])] : [],
    }
  }),
)

const running = ref(false)
const error = ref<ShownError | null>(null)
const confirming = ref(false)

/**
 * Значения формы; числа — числом, пустые — null. Значения initial, для которых нет поля (порядок показа), — как есть.
 * Ошибка разбора числа — исключение с текстом для пользователя.
 */
function values(): SpecValues {
  const res: SpecValues = {}
  for (const [name, v] of Object.entries(props.initial)) {
    if (!props.fields.some((f) => f.name === name)) res[name] = v
  }
  for (const { f, text, choice, choices } of entries) {
    if (f.kind === 'bool') res[f.name] = choice === 'yes'
    else if (f.kind === 'single' || f.kind === 'fault')
      res[f.name] = choice === null || Array.isArray(choice) ? null : Number(choice)
    else if (f.kind === 'multi') res[f.name] = (Array.isArray(choices) ? choices : []).map(Number)
    else if (f.kind === 'number') {
      const s = text.trim().replace(',', '.')
      if (s !== '' && !Number.isFinite(Number(s))) throw new Error(t('Неверное число: {field}', { field: t(f.label) }))
      res[f.name] = s === '' ? null : Number(s)
    } else res[f.name] = text.trim() || null
  }
  return res
}

// поля, видимые при текущих значениях
const shown = computed(() => {
  let current: SpecValues
  try {
    current = values()
  } catch {
    current = props.initial
  }
  return entries.filter((e) => !e.f.visible || e.f.visible(current))
})

async function onSave() {
  let v: SpecValues
  try {
    v = values()
  } catch (e) {
    error.value = formError(e instanceof Error ? e.message : String(e))
    return
  }
  if (shown.value.some((e) => e.f.required && (v[e.f.name] === null || v[e.f.name] === undefined))) {
    error.value = formError(t('Заполните обязательные поля'))
    return
  }
  error.value = null
  running.value = true
  try {
    emit('done', await props.save(v))
  } catch (e) {
    error.value = shownError(e)
  } finally {
    running.value = false
  }
}

async function onRemove() {
  if (!props.remove) return
  if (!confirming.value) {
    confirming.value = true
    return
  }
  error.value = null
  running.value = true
  try {
    await props.remove()
    emit('done', 'deleted')
  } catch (e) {
    confirming.value = false
    error.value = shownError(e)
  } finally {
    running.value = false
  }
}
</script>

<style scoped lang="scss">
.spec-dialog__wide {
  grid-column: 1 / -1;
}
</style>
