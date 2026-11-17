<template>
  <span class="user-option" :class="{ 'user-option--compact': compact }">
    <span class="user-option__main">
      <!-- ФИО и плашка «вы» не переносятся: строка целиком (white-space: nowrap) -->
      <span class="user-option__name"
        >{{ name
        }}<template v-if="me"
          >&nbsp;<InlineBadge>{{ t('вы') }}</InlineBadge></template
        ></span
      >
      <span v-if="!plain" class="user-option__duty" :class="{ 'user-option__match': dutyMatch && !!duty }">
        {{ duty || '—' }}
      </span>
    </span>
    <span v-if="!plain && parts.length > 0" class="user-option__place">
      <template v-for="(p, i) in parts" :key="i">
        <template v-if="i > 0">, </template>
        <span :class="{ 'user-option__match': p.match }">{{ p.text }}</span>
      </template>
    </span>
  </span>
</template>

<script setup lang="ts">
import { computed } from 'vue'

import { t } from '@/boot/i18n'
import InlineBadge from './InlineBadge.vue'

/** Часть места сотрудника: текст; match — совпадает с тем, для чего выбирают (жирным). */
export interface UserPlacePart {
  text: string
  match?: boolean
}

/**
 * «Список ФИО» — строка выбора сотрудника, одна на всё приложение: в выпадающих списках, окнах и листах выбора на
 * десктопе и телефоне.
 *
 * - Слева: ФИО (me — текущий пользователь: серая инлайн-плашка «вы»), под ФИО — должность или служба (нет — тире).
 * - Справа: где сотрудник — строкой («рейс 002», «депо станции») или частями через запятую («ваг. 12, ваг. 13»).
 * - Совпадения с тем, для чего выбирают, — жирным: dutyMatch — должность, match у части места — эта часть.
 * - plain — строка не сотрудника («Не назначен»): только текст. compact — для поля с выбранным: строки плотнее, чтобы
 *   уместились в высоту поля.
 *
 *   <UserOption :name="u.name" :me="isMe(u)" duty="Проводник" place="рейс 002" />
 */
const props = withDefaults(
  defineProps<{
    name: string
    me?: boolean
    duty?: string
    dutyMatch?: boolean
    place?: string | UserPlacePart[]
    plain?: boolean
    compact?: boolean
  }>(),
  { me: false, duty: '', dutyMatch: false, place: '', plain: false, compact: false },
)

const parts = computed<UserPlacePart[]>(() =>
  typeof props.place === 'string' ? (props.place ? [{ text: props.place }] : []) : props.place,
)
</script>

<style scoped lang="scss">
.user-option {
  display: flex;
  flex: 1;
  align-items: flex-start;
  gap: 0.5rem;
  // во всю ширину строки и там, где родитель — не flex: расположение прижато к правому краю
  width: 100%;
  min-width: 0;
}

.user-option__main {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
}

// ФИО не разрывается переносом
.user-option__name {
  white-space: nowrap;
}

.user-option__duty {
  color: $zs-text-muted;
  font-size: 0.8rem;
}

.user-option__place {
  flex: none;
  max-width: 50%;
  color: $zs-text-muted;
  font-size: 0.8rem;
  text-align: right;
}

.user-option__match {
  color: $zs-text;
  font-weight: 700;
}

// поле с выбранным: строки плотнее, чтобы уместились в высоту поля
.user-option--compact {
  align-items: center;

  .user-option__duty {
    overflow: hidden;
    white-space: nowrap;
    text-overflow: ellipsis;
    line-height: 1.1;
  }

  .user-option__place {
    font-size: 0.7rem;
    white-space: nowrap;
  }
}
</style>
