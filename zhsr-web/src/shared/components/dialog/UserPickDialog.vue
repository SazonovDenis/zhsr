<template>
  <FormDialog
    v-model:show="show"
    :title="title"
    :ok-text="okText"
    :extra-actions="extraActions"
    :running="saving"
    :error="loadError"
    @ok="apply(picked)"
  >
    <LoadSpinner v-if="loading" />
    <!-- ошибка загрузки — вместо списка. Ошибка сохранения — под списком, с крестиком; высота окна постоянная: при
         ошибке список становится ниже, а не окно выше; любое касание списка убирает ошибку -->
    <div v-else-if="!loadError" class="user-pick-wrap">
      <div class="user-pick" @pointerdown="saveError = null">
        <button
          v-for="o in options"
          :key="o.id ?? 0"
          type="button"
          class="user-pick__item q-py-xs q-px-sm"
          :class="{ 'user-pick__item--on': picked === o.id }"
          data-dialog-pick
          @click="picked = o.id"
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
      </div>
      <ErrorBox v-if="saveError" :error="saveError" closable class="user-pick__error" @close="saveError = null" />
    </div>
  </FormDialog>
</template>

<script setup lang="ts">
import { computed, ref, shallowRef, watch } from 'vue'

import type { UserRef } from '@/api/requests'
import { t } from '@/boot/i18n'
import FormDialog from '@/shared/components/dialog/FormDialog.vue'
import type { DialogAction } from '@/shared/components/dialog/types'
import ErrorBox from '@/shared/components/ErrorBox.vue'
import LoadSpinner from '@/shared/components/LoadSpinner.vue'
import type { UserPlacePart } from '@/shared/components/UserOption.vue'
import UserOption from '@/shared/components/UserOption.vue'
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

/**
 * Окно выбора сотрудника — одно для всех назначений на десктопе (исполнитель заявки, ответственный за вагоны):
 * «Не назначен» и сотрудники строками «Списка ФИО» (UserOption), выбор нажатием, применение основной кнопкой или
 * двойным кликом; «Назначить себя» — сразу назначить текущего пользователя, если он есть в списке. load — загрузить
 * сотрудников по порядку показа (ошибка — в окне); current — назначенный сейчас: он в списке, даже если load его не
 * вернул. Выбор сохраняет само окно через save: спиннер на кнопке, ошибка — в окне, окно остаётся открытым; удалось —
 * окно закрывается событием done с id сотрудника (null — не назначен). Открывается через openDialog.
 */
const props = defineProps<{
  title: string
  okText: string
  current: UserRef | null
  load: () => Promise<UserPickRow[]>
  /** сохранить выбор: id сотрудника, null — не назначен; ошибка — бросить, окно её покажет */
  save: (usr: number | null) => Promise<void>
}>()

const show = defineModel<boolean>('show', { default: false })
const emit = defineEmits<{ done: [result: { usr: number | null }] }>()

const rows = shallowRef<UserPickRow[]>([])
const loading = ref(true)
const loadError = ref<ShownError | null>(null)
const saving = ref(false)
const saveError = ref<ShownError | null>(null)
const picked = ref<number | null>(props.current?.id ?? null)
// другой выбор — ошибка прошлого сохранения уже не о нём
watch(picked, () => (saveError.value = null))

const options = computed<(Omit<UserPickRow, 'id'> & { id: number | null })[]>(() => {
  const list: (Omit<UserPickRow, 'id'> & { id: number | null })[] = [...rows.value]
  const current = props.current
  if (current && !list.some((u) => u.id === current.id))
    list.unshift({ id: current.id, name: current.name, me: isMe(current) })
  return [{ id: null, name: t('Не назначен') }, ...list]
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
    await props.save(usr)
    emit('done', { usr })
  } catch (e) {
    saveError.value = shownError(e)
  } finally {
    saving.value = false
  }
}

// «Назначить себя» — сразу, без выбора в списке; неактивна, пока список грузится или если себя в нём нет
const me = computed(() => currentUser.value?.id ?? null)
const canPickMe = computed(() => !loading.value && me.value !== null && options.value.some((o) => o.id === me.value))
const extraActions = computed<DialogAction[]>(() => [
  {
    name: 'me',
    title: 'Назначить себя',
    disabled: !canPickMe.value || saving.value,
    exec: () => void apply(me.value),
  },
])
</script>

<style scoped lang="scss">
// постоянная высота: появление и скрытие ошибки сохранения не меняют высоту окна — место отдаёт список
.user-pick-wrap {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  height: 60vh;
}

.user-pick__error {
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
