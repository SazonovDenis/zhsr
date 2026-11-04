<template>
  <FormDialog
    v-model:show="show"
    :title="user ? 'Пользователь' : 'Новый пользователь'"
    :ok-text="user ? 'Сохранить' : 'Создать пользователя'"
    width="40rem"
    hint="Заданный здесь пароль пользователь сменит при первом входе."
    :running="running"
    :error="error"
    @ok="save"
  >
    <FormSection title="Сотрудник" columns="repeat(2, minmax(0, 1fr))">
      <FormField label="ФИО *" class="admin-user__wide" :style="place('fullName')">
        <TextInput v-model="draft.fullName" placeholder="Фамилия Имя Отчество" />
      </FormField>
      <FormField label="Логин *" :style="place('login')">
        <TextInput v-model="draft.login" mono placeholder="Как правило, ИИН" />
      </FormField>
      <FormField label="Телефон" :style="place('phone')">
        <TextInput v-model="draft.phone" mono placeholder="+7XXXXXXXXXX" />
      </FormField>
    </FormSection>

    <FormSection title="Роли и депо" divided columns="repeat(2, minmax(0, 1fr))">
      <FormField label="Роли" class="admin-user__wide" :style="place('roles')">
        <CbList v-model="roles" placeholder="Выберите…" :values="roleChoices" :source="roleLoad" />
      </FormField>
      <FormField label="Депо" :style="place('depot')">
        <CbList v-model="depot" single placeholder="Нет" :values="depotChoices" :source="depotLoad" />
      </FormField>
      <FormField label="Категория проводника" :style="place('categoryType')">
        <CbList v-model="category" single placeholder="Нет" :values="categoryChoices" :source="categoryLoad" />
      </FormField>
    </FormSection>

    <FormSection title="Учётная запись" divided columns="max-content minmax(0, 1fr)">
      <FormField label="Отключена" :style="place('disabled')">
        <CbSegment v-model="disabled" single required :values="yesNo" />
      </FormField>
      <FormField :label="user ? 'Новый пароль' : 'Пароль *'" :style="place('password')">
        <TextInput v-model="draft.password" password :placeholder="user ? 'Пусто — не менять' : ''" />
      </FormField>
    </FormSection>
  </FormDialog>
</template>

<script setup lang="ts">
import { reactive, ref, shallowRef } from 'vue'

import type { AdminUser, AdminUserDraft } from '@/api/admin'
import { createAdminUser, updateAdminUser } from '@/api/admin'
import { t } from '@/boot/i18n'
import type { ShownError } from '@/shared/errors'
import { formError, shownError } from '@/shared/errors'
import type { ChoiceId, ChoiceItem } from '@/shared/components/choice/choice'
import CbList from '@/shared/components/choice/CbList.vue'
import CbSegment from '@/shared/components/choice/CbSegment.vue'
import { provideBusy } from '@/shared/busy'
import { useLoad } from '@/shared/loadable'
import FormDialog from '@/shared/components/dialog/FormDialog.vue'
import FormField from '@/shared/components/form/FormField.vue'
import FormSection from '@/shared/components/form/FormSection.vue'
import TextInput from '@/shared/components/form/TextInput.vue'
import { fieldRank } from '../shared/adminFieldOrder'
import { adminOptions } from '../table/adminTables'

/**
 * Карточка сотрудника: ФИО, логин, телефон, роли, депо, категория проводника, отключение и пароль. user = null — новый
 * сотрудник, пароль обязателен. Сотрудник не удаляется, а отключается. Результат (событие done) — сохранённый
 * сотрудник.
 */

const props = defineProps<{ user: AdminUser | null }>()

const show = defineModel<boolean>('show', { default: false })
// ожидание диалога: спиннер в подвале (FormDialog) — на всю цепочку загрузок формы
provideBusy()
const emit = defineEmits<{ done: [user: AdminUser] }>()

// порядок полей внутри разделов — adminFieldOrder; разделы формы постоянные, поэтому место задаётся сеткой раздела
const place = (field: string) => ({ order: fieldRank('usr', field) })

const yesNo: ChoiceItem[] = [
  { id: 'yes', text: 'Да' },
  { id: 'no', text: 'Нет' },
]

const draft = reactive({
  login: props.user?.login ?? '',
  fullName: props.user?.fullName ?? '',
  phone: props.user?.phone ?? '',
  password: '',
})
const roles = ref<ChoiceId[]>([...(props.user?.roles ?? [])])
const depot = ref<ChoiceId | null>(props.user?.depot ?? null)
const category = ref<ChoiceId | null>(props.user?.categoryType ?? null)
const disabled = ref<ChoiceId | null>(props.user?.disabled ? 'yes' : 'no')

const roleChoices = shallowRef<ChoiceItem[]>([])
const depotChoices = shallowRef<ChoiceItem[]>([])
const categoryChoices = shallowRef<ChoiceItem[]>([])

const running = ref(false)
// ошибка формы: проверки (formError) или запроса (shownError, запрос второй строкой); null — ошибки нет
const error = ref<ShownError | null>(null)

// загрузка формы — сразу в setup и в счёт ожидания диалога: спиннер в подвале с первой отрисовки
// варианты выпадающих списков — свои загрузки: форма их не ждёт, ошибка — в самом списке
const toChoices = (list: { id: number; text: string }[]) => list.map((o) => ({ id: o.id, text: o.text }))
const roleLoad = useLoad(async () => {
  roleChoices.value = toChoices(await adminOptions('role_type'))
})
const depotLoad = useLoad(async () => {
  depotChoices.value = toChoices(await adminOptions('depot'))
})
const categoryLoad = useLoad(async () => {
  categoryChoices.value = toChoices(await adminOptions('category_type'))
})
void roleLoad.run()
void depotLoad.run()
void categoryLoad.run()

function toDraft(): AdminUserDraft {
  return {
    login: draft.login.trim(),
    fullName: draft.fullName.trim(),
    phone: draft.phone.trim(),
    roles: roles.value.map(Number),
    depot: depot.value === null ? null : Number(depot.value),
    categoryType: category.value === null ? null : Number(category.value),
    disabled: disabled.value === 'yes',
    password: draft.password,
  }
}

async function save() {
  if (!draft.login.trim() || !draft.fullName.trim() || (!props.user && !draft.password)) {
    error.value = formError(t('Заполните обязательные поля'))
    return
  }
  error.value = null
  running.value = true
  try {
    const user = props.user ? await updateAdminUser(props.user.id, toDraft()) : await createAdminUser(toDraft())
    emit('done', user)
  } catch (e) {
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
.admin-user__wide {
  grid-column: 1 / -1;
}
</style>
