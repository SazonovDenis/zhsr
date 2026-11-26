<template>
  <FormDialog
    v-model:show="show"
    min-height="lg"
    :title="title"
    :ok-text="okText"
    :extra-actions="extraActions"
    :running="saving"
    :error="saveError ?? loadError"
    @ok="multiple ? applyMany() : apply(picked)"
  >
    <LoadSpinner v-if="loading" />
    <!-- ошибка загрузки — вместо списка. Ошибка сохранения — под списком, с крестиком; высота окна постоянная: при
         ошибке список становится ниже, а не окно выше; любое касание списка убирает ошибку -->
    <div v-else-if="!loadError" class="user-pick-wrap">
      <!-- группы сотрудников: переключатель — при двух группах и больше; отметки других групп сохраняются -->
      <CbSegment
        v-if="groups.length > 1"
        v-model="group"
        single
        required
        :values="groupItems"
        class="user-pick__groups"
      />
      <div class="user-pick" @pointerdown="saveError = null" @keydown="onArrow">
        <!-- multiple: подсвечено может быть несколько, нажатие подсвечивает или снимает; двойной клик не применяет -->
        <button
          v-for="o in options"
          :key="o.id ?? 0"
          type="button"
          class="user-pick__item q-py-xs q-px-sm"
          :class="{ 'user-pick__item--on': isOn(o.id) }"
          :data-dialog-pick="multiple ? undefined : ''"
          @click="pick(o.id)"
        >
          <!-- «Список ФИО»; «Не назначен» — только текст -->
          <UserOption
            :name="o.name"
            :me="o.me ?? false"
            :duty="o.duty ?? ''"
            :duty-match="o.dutyMatch ?? false"
            :place="o.place ?? ''"
            :plain="o.id === null"
          />
        </button>
        <div v-if="emptyText" class="user-pick__empty q-px-sm">{{ t(emptyText) }}</div>
      </div>
    </div>
  </FormDialog>
</template>

<script setup lang="ts">
import { computed, ref, shallowRef, watch } from 'vue'

import type { UserRef } from '@/api/requests'
import { t } from '@/boot/i18n'
import type { ChoiceItem } from '@/shared/components/choice/choice'
import CbSegment from '@/shared/components/choice/CbSegment.vue'
import FormDialog from '@/shared/components/dialog/FormDialog.vue'
import type { DialogAction } from '@/shared/components/dialog/types'
import LoadSpinner from '@/shared/components/LoadSpinner.vue'
import type { UserPlacePart } from '@/shared/components/UserOption.vue'
import UserOption from '@/shared/components/UserOption.vue'
import { arrowTarget } from '@/shared/composables/listArrows'
import type { ShownError } from '@/shared/errors'
import { shownError } from '@/shared/errors'
import { currentUser, isMe } from '@/shared/user'

/** Сотрудник в окне выбора: id, ФИО и данные строки «Списка ФИО» (UserOption). */
export interface UserPickRow {
  id: number
  /** ФИО без пометки «(вы)»: текущего пользователя отмечает me */
  name: string
  me?: boolean
  duty?: string
  dutyMatch?: boolean
  place?: string | UserPlacePart[]
}

/** Группа сотрудников в окне выбора: пункт переключателя и отбор строк load. */
export interface UserPickGroup {
  id: string
  text: string
  filter: (row: UserPickRow) => boolean
  /** что написать под списком, если у load нет ни одной строки группы */
  empty?: string | undefined
}

/**
 * Окно выбора сотрудника — одно для всех назначений на десктопе (исполнитель заявки, ответственный за вагоны):
 * «Не назначен» и сотрудники строками «Списка ФИО» (UserOption), выбор нажатием, применение основной кнопкой или
 * двойным кликом; «Назначить себя» — сразу назначить текущего пользователя, если он есть в списке. load — загрузить
 * сотрудников по порядку показа (ошибка — в окне); current — назначенный сейчас: он в списке, даже если load его не
 * вернул. Выбор сохраняет само окно через save: спиннер на кнопке, ошибка — в окне, окно остаётся открытым; удалось —
 * окно закрывается событием done с id сотрудника (null — не назначен). Открывается через openDialog.
 *
 * multiple — выбор нескольких сотрудников: подсвечено может быть несколько, нажатие подсвечивает или снимает; current — назначенные сейчас (массив), «Не назначен» снимает
 * отмеченных, отметка сотрудника снимает «Не назначен»; «Назначить себя» отмечает текущего пользователя, не сохраняя;
 * сохраняет основная кнопка: save получает массив id (пусто — не назначен), done — { usrs }.
 *
 * groups — группы сотрудников: при двух группах и больше над списком переключатель, список показывает строки load
 * выбранной группы (сначала — первой). «Не назначен» и назначенные сейчас, которых load не вернул, видны в любой
 * группе; отмеченные в другой группе остаются отмеченными.
 */
const props = withDefaults(
  defineProps<{
    title: string
    okText: string
    /** назначенный сейчас; multiple — назначенные сейчас */
    current: UserRef | UserRef[] | null
    load: () => Promise<UserPickRow[]>
    /**
     * сохранить выбор: id сотрудника, null — не назначен; multiple — массив id, пусто — не назначен; ошибка — бросить,
     * окно её покажет
     */
    save: ((usr: number | null) => Promise<void>) | ((usrs: number[]) => Promise<void>)
    multiple?: boolean
    groups?: UserPickGroup[]
  }>(),
  { multiple: false, groups: () => [] },
)

const show = defineModel<boolean>('show', { default: false })
const emit = defineEmits<{ done: [result: { usr: number | null } | { usrs: number[] }] }>()

/** Назначенные сейчас списком. */
const currentList = computed<UserRef[]>(() => {
  const c = props.current
  if (Array.isArray(c)) return c
  return c ? [c] : []
})

const rows = shallowRef<UserPickRow[]>([])
const loading = ref(true)
const loadError = ref<ShownError | null>(null)
const saving = ref(false)
const saveError = ref<ShownError | null>(null)
const picked = ref<number | null>(currentList.value[0]?.id ?? null)
// multiple: отмеченные сотрудники
const pickedMany = ref<number[]>(currentList.value.map((u) => u.id))
// другой выбор — ошибка прошлого сохранения уже не о нём
watch([picked, pickedMany], () => (saveError.value = null), { deep: true })

/** Отмечен ли вариант; multiple: «Не назначен» — когда не отмечен никто. */
function isOn(id: number | null): boolean {
  if (!props.multiple) return picked.value === id
  return id === null ? pickedMany.value.length === 0 : pickedMany.value.includes(id)
}

/** Нажатие на вариант: выбрать; multiple — отметить или снять. */
function pick(id: number | null) {
  if (!props.multiple) {
    picked.value = id
    return
  }
  if (id === null) pickedMany.value = []
  else
    pickedMany.value = pickedMany.value.includes(id)
      ? pickedMany.value.filter((u) => u !== id)
      : [...pickedMany.value, id]
}

/** Стрелки вверх и вниз — выбор соседнего варианта; multiple — только фокус, отмечает пробел. */
function onArrow(e: KeyboardEvent) {
  const el = arrowTarget(e, '.user-pick__item', props.multiple ? '.user-pick__item:focus' : '.user-pick__item--on')
  el?.focus()
  if (!props.multiple) el?.click()
}

const group = ref<string | null>(props.groups[0]?.id ?? null)
const groupItems = computed<ChoiceItem[]>(() => props.groups.map((g) => ({ id: g.id, text: g.text })))

const options = computed<(Omit<UserPickRow, 'id'> & { id: number | null })[]>(() => {
  const g = props.groups.length > 1 ? props.groups.find((x) => x.id === group.value) : undefined
  const list: (Omit<UserPickRow, 'id'> & { id: number | null })[] = g ? rows.value.filter(g.filter) : [...rows.value]
  for (const current of [...currentList.value].reverse()) {
    if (!rows.value.some((u) => u.id === current.id) && !list.some((u) => u.id === current.id))
      list.unshift({ id: current.id, name: current.name, me: isMe(current) })
  }
  return [{ id: null, name: t('Не назначен') }, ...list]
})

/** Пояснение показанной группы без сотрудников; пусто — не показывать. */
const emptyText = computed(() => {
  const g = props.groups.find((x) => x.id === group.value)
  return g?.empty && !rows.value.some(g.filter) ? g.empty : ''
})

void props
  .load()
  .then((list) => (rows.value = list))
  .catch((e: unknown) => (loadError.value = shownError(e)))
  .finally(() => (loading.value = false))

/** Сохранить выбор; удалось — окно закрывается с результатом, нет — ошибка в окне, окно остаётся открытым. */
async function apply(usr: number | null) {
  if (saving.value) return
  saving.value = true
  saveError.value = null
  try {
    await (props.save as (usr: number | null) => Promise<void>)(usr)
    emit('done', { usr })
  } catch (e) {
    saveError.value = shownError(e)
  } finally {
    saving.value = false
  }
}

/** multiple: сохранить отмеченных; удалось — окно закрывается с результатом, нет — ошибка в окне. */
async function applyMany() {
  if (saving.value) return
  saving.value = true
  saveError.value = null
  const usrs = [...pickedMany.value]
  try {
    await (props.save as (usrs: number[]) => Promise<void>)(usrs)
    emit('done', { usrs })
  } catch (e) {
    saveError.value = shownError(e)
  } finally {
    saving.value = false
  }
}

// «Назначить себя» — сразу, без выбора в списке (multiple — отметить себя); неактивна, пока список грузится, если
// себя в нём нет или (multiple) уже отмечен
const me = computed(() => currentUser.value?.id ?? null)
const canPickMe = computed(
  () =>
    !loading.value &&
    me.value !== null &&
    // в списке любой группы, не только показанной
    (rows.value.some((o) => o.id === me.value) || currentList.value.some((o) => o.id === me.value)) &&
    !(props.multiple && pickedMany.value.includes(me.value)),
)
const extraActions = computed<DialogAction[]>(() => [
  {
    name: 'me',
    title: 'Назначить себя',
    disabled: !canPickMe.value || saving.value,
    exec: () => {
      if (props.multiple) pick(me.value)
      else void apply(me.value)
    },
  },
])
</script>

<style scoped lang="scss">
// занимает всю высоту окна (высота окна постоянная): появление и скрытие ошибки сохранения место берут у списка
.user-pick-wrap {
  display: flex;
  flex: 1 1 0;
  flex-direction: column;
  gap: 0.5rem;
  min-height: 0;
}

.user-pick__groups {
  flex: none;
}

.user-pick {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 0.3rem;
  min-height: 0;
  // список шире полей окна (1.5rem): до края окна — 0.5rem
  margin: 0 -1rem;
  overflow-y: auto;
}

.user-pick__empty {
  color: $zs-text-muted;
  font-size: 0.8rem;
}

.user-pick__item {
  display: flex;
  border: 0.0625rem solid $zs-border;
  border-radius: 0.4rem;
  background: #fff;
  color: $zs-text;
  font: inherit;
  text-align: left;
  cursor: pointer;

  &--on {
    border-color: $primary;
    background: $zs-selected-bg;
    color: $zs-selected-fg;
  }
}
</style>
