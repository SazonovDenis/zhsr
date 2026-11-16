<template>
  <FormDialog
    v-model:show="show"
    :title="current ? 'Переназначить исполнителя' : 'Назначить исполнителя'"
    :ok-text="current ? 'Переназначить' : 'Назначить'"
    :extra-actions="extraActions"
    :error="error"
    @ok="ok"
  >
    <LoadSpinner v-if="loading" />
    <div v-else-if="!error" class="request-assign">
      <button
        v-for="o in options"
        :key="o.id ?? 0"
        type="button"
        class="request-assign__item q-py-xs q-px-md"
        :class="{ 'request-assign__item--on': picked === o.id }"
        data-dialog-pick
        @click="picked = o.id"
      >
        <StaffOption :name="o.name" :candidate="o.candidate" :wagon-number="wagonNumber" />
      </button>
    </div>
  </FormDialog>
</template>

<script setup lang="ts">
import { computed, ref, shallowRef } from 'vue'

import type { StaffCandidate, UserRef } from '@/api/requests'
import { t } from '@/boot/i18n'
import FormDialog from '@/shared/components/dialog/FormDialog.vue'
import type { DialogAction } from '@/shared/components/dialog/types'
import LoadSpinner from '@/shared/components/LoadSpinner.vue'
import type { ShownError } from '@/shared/errors'
import { shownError } from '@/shared/errors'
import StaffOption from '@/shared/requests/StaffOption.vue'
import { sortStaff } from '@/shared/requests/staff'
import { currentUser, userName } from '@/shared/user'

/**
 * Назначение исполнителя заявки во всплывающем окне — как на телефоне: список сотрудников службы и «Не назначен»,
 * выбор нажатием, применение кнопкой «Назначить» или двойным кликом по сотруднику; «Назначить себя» — сразу назначить текущего пользователя, если он есть
 * в списке. load — загрузить, кому можно назначить; current — исполнитель сейчас; wagonNumber — номер вагона заявки. У
 * сотрудника бригады рейса справа — должность и вагоны, совпадения с заявкой жирным; предпочтительные — первыми.
 * Результат — событие done с id сотрудника (null — снять назначение). Открывается через openDialog.
 */
const props = defineProps<{
  current: UserRef | null
  load: () => Promise<StaffCandidate[]>
  wagonNumber: string | null
}>()

const show = defineModel<boolean>('show', { default: false })
const emit = defineEmits<{ done: [result: { executor: number | null }] }>()

const staff = shallowRef<StaffCandidate[]>([])
const loading = ref(true)
const error = ref<ShownError | null>(null)
const picked = ref<number | null>(props.current?.id ?? null)

// текущий исполнитель есть в списке, даже если он из другой службы
const options = computed(() => {
  type Option = { id: number | null; name: string; candidate: StaffCandidate | null }
  const list: Option[] = sortStaff(staff.value, props.wagonNumber).map((c) => ({
    id: c.usr.id,
    name: userName(c.usr),
    candidate: c,
  }))
  const current = props.current
  if (current && !list.some((u) => u.id === current.id)) {
    list.unshift({ id: current.id, name: userName(current), candidate: null })
  }
  return [{ id: null, name: t('Не назначен'), candidate: null }, ...list]
})

void props
  .load()
  .then((list) => (staff.value = list))
  .catch((e: unknown) => (error.value = shownError(e)))
  .finally(() => (loading.value = false))

function ok() {
  emit('done', { executor: picked.value })
}

// «Назначить себя» — сразу, без выбора в списке; неактивна, пока список грузится или если себя в нём нет
const me = computed(() => currentUser.value?.id ?? null)
const canPickMe = computed(() => !loading.value && me.value !== null && options.value.some((o) => o.id === me.value))
const extraActions = computed<DialogAction[]>(() => [
  {
    name: 'me',
    title: 'Назначить себя',
    disabled: !canPickMe.value,
    exec: () => emit('done', { executor: me.value }),
  },
])
</script>

<style scoped lang="scss">
.request-assign {
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
  max-height: 60vh;
  overflow-y: auto;
}

.request-assign__item {
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
