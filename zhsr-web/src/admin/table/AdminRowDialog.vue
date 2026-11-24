<template>
  <FormDialog
    v-model:show="show"
    min-height="lg"
    :width="hasMap ? '70rem' : '35rem'"
    :title="row ? 'Изменение записи' : 'Новая запись'"
    :ok-text="row ? 'Сохранить' : 'Добавить'"
    :running="running"
    :error="error"
    @ok="save"
  >
    <div class="admin-row__body" :class="{ 'admin-row__body--map': hasMap }">
      <div class="admin-row__fields">
        <FormSection v-for="g in groups" :key="g.title" :title="g.title">
          <FormField v-for="f in g.fields" :key="f.col.name" :label="label(f.col)" @focusin="onFocus(f)">
            <CbSegment v-if="f.col.type === 'BOOL'" v-model="f.choice" single required :values="yesNo" />
            <CbList
              v-else-if="f.col.type === 'REF'"
              :ref="(el) => setList(f, el)"
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
        <!-- пояснение к карте: внизу, у её края -->
        <div v-if="hint" class="admin-row__hint zs-sub">{{ t(hint) }}</div>
      </div>
      <!-- справа — координаты (поля x и y): щелчок по карте заполняет поля, ввод чисел переносит точку -->
      <FormField v-if="hasPoint">
        <PointPicker v-model="point" :text="pointText" :marks="marks" :source="marksLoad" class="admin-row__map" />
      </FormField>
      <!-- справа — станции: щелчок подставляет станцию в поле, где фокус (был последним) -->
      <FormField v-else-if="hasMap">
        <AdminStationPick
          :stations="stationFields.map((f) => stationOf(f))"
          :active="active"
          class="admin-row__map"
          @pick="pickStation"
        />
      </FormField>
    </div>

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
import { computed, reactive, ref, shallowRef } from 'vue'

import type { AdminColumn, AdminRow, AdminTable } from '@/api/admin'
import { createAdminRow, deleteAdminRow, updateAdminRow } from '@/api/admin'
import type { MapPoint } from '@/api/stages'
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
import type { PointMark } from '@/shared/components/map/mapMarks'
import PointPicker from '@/shared/components/map/PointPicker.vue'
import DatePicker from '@/shared/components/period/DatePicker.vue'
import AdminStationPick from './AdminStationPick.vue'
import { ADMIN_MAPS, fetchStationMarks } from './adminMaps'
import { sortFields } from '../shared/adminFieldOrder'
import { adminOptions, SORT_ORDER } from './adminTables'

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
  // порядок показа в форме не вводится: его меняют кнопки перестановки в таблице, новой записи ставит сервер
  sortFields(props.table.name, props.table.columns, (col) => col.name)
    .filter((col) => col.name !== SORT_ORDER)
    .map((col) => {
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

// координаты станции: поля формы x (долгота) и y (широта)
const fieldX = fields.find((f) => f.col.name === 'x')
const fieldY = fields.find((f) => f.col.name === 'y')
const hasPoint = !!fieldX && !!fieldY
// таблица с картой; null — карты нет
const mapDef = ADMIN_MAPS[props.table.name] ?? null
const hasMap = !!mapDef
// разделы формы; поле вне разделов — в первом
const sections = mapDef?.sections ?? [{ title: props.table.title, fields: [] }]
const groups = sections.map((section, i) => ({
  title: section.title,
  fields: fields.filter((f) => {
    const at = sections.findIndex((s) => s.fields.includes(f.col.name))
    return at === i || (at === -1 && i === 0)
  }),
}))

// поля станций: щелчок по карте подставляет станцию в поле, где фокус; фокус не в них — в последнее, где он был;
// сначала — первое пустое
const stationFields = (mapDef?.stations ?? []).flatMap((name) => fields.filter((f) => f.col.name === name))
const active = ref(
  Math.max(
    0,
    stationFields.findIndex((f) => f.choice === null),
  ),
)
const lists = new Map<Field, { focus: () => void }>()
const hint = hasPoint
  ? 'Координаты можно указать щелчком по карте'
  : stationFields.length > 1
    ? 'Станцию можно указать щелчком по карте: она подставится в поле, где фокус'
    : stationFields.length > 0
      ? 'Станцию можно указать щелчком по карте'
      : ''

function setList(f: Field, el: unknown) {
  if (el) lists.set(f, el as { focus: () => void })
  else lists.delete(f)
}

function onFocus(f: Field) {
  const i = stationFields.indexOf(f)
  if (i !== -1) active.value = i
}

function stationOf(f: Field): number | null {
  return typeof f.choice === 'number' ? f.choice : null
}

function pickStation(station: number) {
  const f = stationFields[active.value]
  if (!f) return
  f.choice = station
  // щелчок по карте увёл фокус — вернуть: видно, в какое поле подставится следующая станция
  lists.get(f)?.focus()
}

// подпись точки — название из формы
const fieldName = fields.find((f) => f.col.name === 'name')
const pointText = computed(() => fieldName?.text.trim() ?? '')

// остальные станции на карте — своя загрузка карты: форма её не ждёт, ошибка — на карте
const marks = shallowRef<PointMark[]>([])
const marksLoad = useLoad(async () => {
  marks.value = (await fetchStationMarks()).filter((s) => s.id !== props.row?.id)
})
if (hasPoint) void marksLoad.run()

function coordinate(text: string, max: number): number | null {
  const value = text.trim() ? Number(text) : NaN
  return Math.abs(value) <= max ? value : null
}

// точка по полям; null — поля пусты или координаты неверны
const point = computed<MapPoint | null>({
  get: () => {
    const x = coordinate(fieldX?.text ?? '', 180)
    const y = coordinate(fieldY?.text ?? '', 90)
    return x === null || y === null ? null : { x, y }
  },
  set: (p) => {
    if (!p || !fieldX || !fieldY) return
    fieldX.text = String(p.x)
    fieldY.text = String(p.y)
  },
})

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
// с картой: поля слева, карта справа — на всю высоту тела диалога
.admin-row__body--map {
  display: grid;
  flex: 1 0 auto;
  grid-template-columns: minmax(0, 2fr) minmax(0, 3fr);
  gap: 1.4rem;
}

.admin-row__fields {
  display: flex;
  flex-direction: column;
  gap: 1.4rem;
}

.admin-row__hint {
  margin-top: auto;
  text-align: right;
}

.admin-row__map {
  flex: 1 0 20rem;
}

.admin-row__delete-btn {
  min-height: var(--zs-control-height);
  font-weight: 600;
}
</style>
