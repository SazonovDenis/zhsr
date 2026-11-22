<template>
  <div class="trip-ready">
    <div class="trip-ready__caption">{{ t('Готовность') }}</div>
    <div v-for="c in items" :key="c.title" class="trip-ready__item">
      <span class="trip-ready__icon" :class="{ 'trip-ready__icon--ok': c.ok }">{{ c.ok ? '✓' : '✕' }}</span>
      <span>
        <span class="trip-ready__title">{{ c.title }}</span>
        <!-- ЛНП и ПЭМ — ФИО разметкой (плашка «вы»), остальное — текстом -->
        <span v-if="c.people" class="trip-ready__muted">
          <template v-for="(p, i) in c.people" :key="p.label">
            <template v-if="i > 0"> • </template>
            {{ p.label }}
            <UserName v-if="p.user" :user="p.user" /><template v-else>—</template>
          </template>
        </span>
        <span v-else class="trip-ready__muted">{{ c.detail }}</span>
      </span>
    </div>
    <div v-if="ready" class="trip-ready__verdict">{{ t('Поезд готов к отправлению') }}</div>
    <div>
      <q-btn
        unelevated
        no-caps
        color="primary"
        :disable="!!markReason || !ready || running"
        :title="markReason || undefined"
        :label="t('Отметить отправление')"
        @click="emit('mark')"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

import type { TripCrew } from '@/api/trips'
import { t } from '@/boot/i18n'
import UserName from '@/shared/components/UserName.vue'
import { tripReadiness } from '@/shared/trips/tripReadiness'

/**
 * Готовность рейса к отправлению — для правой панели рейса: пункты готовности (tripReadiness), «Поезд готов к
 * отправлению» и кнопка «Отметить отправление» (событие mark): неактивна, пока рейс не готов или нет прав
 * (markReason — причина). Справочник Station должен быть загружен.
 */
const props = withDefaults(defineProps<{ crew: TripCrew; markReason?: string; running?: boolean }>(), {
  markReason: '',
  running: false,
})
const emit = defineEmits<{ mark: [] }>()

const items = computed(() => tripReadiness(props.crew))
const ready = computed(() => items.value.every((c) => c.ok))
</script>

<style scoped lang="scss">
.trip-ready {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  font-size: 0.8rem;
}

.trip-ready__caption {
  color: $zs-text-muted;
  font-size: 0.9rem;
  font-weight: 600;
}

.trip-ready__item {
  display: flex;
  gap: 0.5rem;
}

.trip-ready__icon {
  flex: none;
  color: $zs-error-fg;
  font-weight: 700;

  &--ok {
    color: $primary;
  }
}

.trip-ready__title {
  display: block;
  font-weight: 600;
}

.trip-ready__muted {
  display: block;
  color: $zs-text-muted;
}

.trip-ready__verdict {
  color: $primary;
  font-weight: 600;
}
</style>
