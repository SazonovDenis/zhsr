<template>
  <FormDialog
    v-model:show="show"
    :title="reassign ? 'Переназначить ответственного' : 'Назначить ответственного'"
    :ok-text="reassign ? 'Переназначить' : 'Назначить'"
    :extra-actions="extraActions"
    :error="error"
    @ok="ok"
  >
    <LoadSpinner v-if="loading" />
    <div v-else-if="!error" class="responsible-assign">
      <button
        v-for="o in options"
        :key="o.id ?? 0"
        type="button"
        class="responsible-assign__item q-py-xs q-px-md"
        :class="{ 'responsible-assign__item--on': picked === o.id }"
        data-dialog-pick
        @click="picked = o.id"
      >
        <span class="responsible-assign__name">{{ o.name }}</span>
        <span v-if="o.who" class="responsible-assign__who">{{ o.who }}</span>
      </button>
    </div>
  </FormDialog>
</template>

<script setup lang="ts">
import { computed, ref, shallowRef } from 'vue'

import type { UserRef } from '@/api/requests'
import { t } from '@/boot/i18n'
import FormDialog from '@/shared/components/dialog/FormDialog.vue'
import type { DialogAction } from '@/shared/components/dialog/types'
import LoadSpinner from '@/shared/components/LoadSpinner.vue'
import type { ShownError } from '@/shared/errors'
import { shownError } from '@/shared/errors'
import { currentUser, userName } from '@/shared/user'
import type { ResponsibleCandidate } from '@/shared/wagons/responsible'
import { fetchResponsibles, responsibleWho } from '@/shared/wagons/responsible'

/**
 * Назначение ответственного за вагоны во всплывающем окне — как назначение исполнителя заявки (RequestAssignDialog):
 * список «Не назначен» и кандидатов, выбор нажатием, применение кнопкой «Назначить» или двойным кликом; «Назначить
 * себя» — сразу назначить текущего пользователя, если он есть в списке. Кандидаты — как у ResponsiblePicker: сотрудники
 * депо станции station, бригада рейса trip и бригады рейсов поезда вагонов wagons; справа — кто сотрудник. current —
 * ответственный сейчас; reassign — ответственный уже назначен (заголовок и кнопка «Переназначить»). Результат — событие
 * done с id сотрудника (null — не назначен). Открывается через openDialog.
 */
const props = withDefaults(
  defineProps<{
    station: number | null
    trip?: number | null
    wagons?: string[]
    current: UserRef | null
    reassign?: boolean
  }>(),
  { trip: null, wagons: () => [], reassign: false },
)

const show = defineModel<boolean>('show', { default: false })
const emit = defineEmits<{ done: [result: { usr: number | null }] }>()

const candidates = shallowRef<ResponsibleCandidate[]>([])
const loading = ref(true)
const error = ref<ShownError | null>(null)
const picked = ref<number | null>(props.current?.id ?? null)

// текущий ответственный есть в списке, даже если его нет среди кандидатов
const options = computed(() => {
  type Option = { id: number | null; name: string; who: string }
  const list: Option[] = candidates.value.map((c) => ({ id: c.id, name: userName(c), who: responsibleWho(c) }))
  const current = props.current
  if (current && !list.some((u) => u.id === current.id)) {
    list.unshift({ id: current.id, name: userName(current), who: '' })
  }
  return [{ id: null, name: t('Не назначен'), who: '' }, ...list]
})

fetchResponsibles(props.station, props.trip, props.wagons)
  .then((list) => (candidates.value = list))
  .catch((e: unknown) => (error.value = shownError(e)))
  .finally(() => (loading.value = false))

function ok() {
  emit('done', { usr: picked.value })
}

// «Назначить себя» — сразу, без выбора в списке; неактивна, пока список грузится или если себя в нём нет
const me = computed(() => currentUser.value?.id ?? null)
const canPickMe = computed(() => !loading.value && me.value !== null && options.value.some((o) => o.id === me.value))
const extraActions = computed<DialogAction[]>(() => [
  {
    name: 'me',
    title: 'Назначить себя',
    disabled: !canPickMe.value,
    exec: () => emit('done', { usr: me.value }),
  },
])
</script>

<style scoped lang="scss">
.responsible-assign {
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
  max-height: 60vh;
  overflow-y: auto;
}

.responsible-assign__item {
  display: flex;
  align-items: baseline;
  gap: 0.5rem;
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

.responsible-assign__name {
  flex: 1;
  min-width: 0;
}

.responsible-assign__who {
  flex: none;
  color: $zs-text-muted;
  font-size: 0.8rem;
}
</style>
