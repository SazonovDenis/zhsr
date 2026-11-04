<template>
  <FormDialog
    v-model:show="show"
    :title="row ? 'Изменение записи' : 'Новая запись'"
    :ok-text="row ? 'Сохранить' : 'Добавить'"
    :running="running"
    :error="error"
    @ok="save"
  >
    <FormSection :title="table.title">
      <FormField v-for="f in fields" :key="f.col.name" :label="label(f.col)">
        <CbSegment
          v-if="f.col.type === 'BOOL'"
          v-model="f.choice"
          class="admin-row__segment"
          single
          required
          :values="yesNo"
        />
        <CbList
          v-else-if="f.col.type === 'REF'"
          v-model="f.choice"
          single
          placeholder="Выберите…"
          :values="f.choices"
          :source="f.load"
        />
        <DatePicker v-else-if="f.col.type === 'DATE'" v-model="f.date" :date-text="f.col.title" />
        <TextInput
          v-else
          v-model="f.text"
          :inputmode="f.col.type === 'INT' ? 'numeric' : f.col.type === 'DECIMAL' ? 'decimal' : 'text'"
        />
      </FormField>
    </FormSection>

    <template #actions-left>
      <q-btn
        v-if="row"
        no-caps
        unelevated
        outline
        color="negative"
        :label="confirming ? t('Нажмите ещё раз, чтобы удалить') : t('Удалить запись')"
        :disable="running"
        class="admin-row__delete-btn zs-focusable"
        @click="remove"
      />
    </template>
  </FormDialog>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'

import type { AdminColumn, AdminRow, AdminTable } from '@/api/admin'
import { createAdminRow, deleteAdminRow, updateAdminRow } from '@/api/admin'
import { t } from '@/boot/i18n'
import type { ShownError } from '@/shared/errors'
import { shownError } from '@/shared/errors'
import type { ChoiceId, ChoiceItem } from '@/shared/components/choice/choice'
import CbList from '@/shared/components/choice/CbList.vue'
import CbSegment from '@/shared/components/choice/CbSegment.vue'
import { provideBusy } from '@/shared/busy'
import type { Load } from '@/shared/loadable'
import { useLoad } from '@/shared/loadable'
import FormDialog from '@/shared/components/dialog/FormDialog.vue'
import FormField from '@/shared/components/form/FormField.vue'
import FormSection from '@/shared/components/form/FormSection.vue'
import TextInput from '@/shared/components/form/TextInput.vue'
import DatePicker from '@/shared/components/period/DatePicker.vue'
import { sortFields } from '../shared/adminFieldOrder'
import { adminOptions } from './adminTables'

/**
 * Диалог записи общего редактора: поля формы строятся по описанию таблицы. row = null — новая запись. У существующей
 * записи слева на панели диалога — «Удалить запись» (второй клик подтверждает); запись, которая используется, сервер
 * не удалит — ошибка в диалоге. Результат (событие done): сохранённая запись или 'deleted'.
 */

const props = defineProps<{ table: AdminTable; row: AdminRow | null }>()

const show = defineModel<boolean>('show', { default: false })
// ожидание диалога: спиннер в подвале (FormDialog) — на всю цепочку загрузок формы
provideBusy()
const emit = defineEmits<{ done: [result: AdminRow | 'deleted'] }>()

const yesNo: ChoiceItem[] = [
  { id: 'yes', text: 'Да' },
  { id: 'no', text: 'Нет' },
]

/**
 * Поле формы. Значение — по виду поля: текст и числа — text строкой, «да/нет» — choice 'yes'/'no', ссылка — choice
 * id записи (варианты — choices), дата — date ISO.
 */
interface Field {
  col: AdminColumn
  text: string
  choice: ChoiceId | null
  date: string | null
  choices: ChoiceItem[]
  /** загрузка вариантов поля-ссылки; null — у поля нет вариантов */
  load: Load | null
}

const fields = reactive<Field[]>(
  sortFields(props.table.name, props.table.columns, (col) => col.name).map((col) => {
    const value = props.row?.[col.name]
    return {
      col,
      text: col.type !== 'BOOL' && col.type !== 'REF' ? textOf(value) : '',
      choice: col.type === 'BOOL' ? (value === true ? 'yes' : 'no') : typeof value === 'number' ? value : null,
      date: col.type === 'DATE' && typeof value === 'string' ? value : null,
      choices: [],
      load: null,
    }
  }),
)

const running = ref(false)
// ошибка формы: проверки (formError) или запроса (shownError, запрос второй строкой); null — ошибки нет
const error = ref<ShownError | null>(null)
const confirming = ref(false)

// загрузка формы — сразу в setup и в счёт ожидания диалога: спиннер в подвале с первой отрисовки
// варианты полей-ссылок — свои загрузки выпадающих списков: форма их не ждёт, ошибка — в самом списке
for (const f of fields) {
  const ref = f.col.ref
  if (f.col.type === 'REF' && ref) {
    f.load = useLoad(async () => {
      f.choices = (await adminOptions(ref)).map((o) => ({ id: o.id, text: o.text }))
    })
    void f.load.run()
  }
}

function textOf(value: unknown): string {
  return typeof value === 'string' || typeof value === 'number' ? String(value) : ''
}

function label(col: AdminColumn): string {
  return col.required ? `${t(col.title)} *` : col.title
}

function values(): Record<string, unknown> {
  const res: Record<string, unknown> = {}
  for (const f of fields) {
    if (f.col.type === 'BOOL') res[f.col.name] = f.choice === 'yes'
    else if (f.col.type === 'REF') res[f.col.name] = f.choice
    else if (f.col.type === 'DATE') res[f.col.name] = f.date
    else res[f.col.name] = f.text.trim() || null
  }
  return res
}

async function save() {
  error.value = null
  running.value = true
  try {
    const row = props.row
      ? await updateAdminRow(props.table.name, props.row.id, values())
      : await createAdminRow(props.table.name, values())
    emit('done', row)
  } catch (e) {
    error.value = message(e)
  } finally {
    running.value = false
  }
}

async function remove() {
  if (!props.row) return
  if (!confirming.value) {
    confirming.value = true
    return
  }
  error.value = null
  running.value = true
  try {
    await deleteAdminRow(props.table.name, props.row.id)
    emit('done', 'deleted')
  } catch (e) {
    confirming.value = false
    error.value = message(e)
  } finally {
    running.value = false
  }
}

function message(e: unknown): ShownError {
  return shownError(e)
}
</script>

<style scoped lang="scss">
.admin-row__segment {
  align-self: flex-start;
}

.admin-row__delete-btn {
  min-height: var(--zs-control-height);
  font-weight: 600;
}
</style>
