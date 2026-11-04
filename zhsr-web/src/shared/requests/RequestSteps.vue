<template>
  <div class="request-steps" :class="{ 'request-steps--large': large }">
    <template v-for="(step, i) in steps" :key="step.label">
      <span v-if="i > 0" class="request-steps__line" :class="{ 'request-steps__line--done': step.state !== 'todo' }" />
      <span class="request-steps__step" :class="`request-steps__step--${step.state}`">
        <span class="request-steps__mark">{{ step.state === 'done' ? '✓' : i + 1 }}</span>
        <span class="request-steps__label">{{ step.label }}</span>
      </span>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

import { requestSteps } from './requestView'

/**
 * Путь заявки «Подана → В работе → Выполнена → Закрыта»: пройденные шаги отмечены галочкой, текущий — обведён.
 * large — крупнее, для телефона.
 */
const props = withDefaults(defineProps<{ status: number; queued?: boolean; large?: boolean }>(), {
  queued: false,
  large: false,
})

const steps = computed(() => requestSteps(props.status, props.queued))
</script>

<style scoped lang="scss">
.request-steps {
  display: flex;
  align-items: flex-start;
  font-size: 0.75rem;
}

.request-steps__step {
  display: flex;
  flex: none;
  flex-direction: column;
  align-items: center;
  gap: 0.25rem;
  width: 4.4rem;
  color: $zs-text-muted;
  text-align: center;
}

// кружок — над линией, которая заходит под соседние шаги
.request-steps__mark {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 1.4rem;
  height: 1.4rem;
  border: 0.125rem solid $zs-border;
  border-radius: 50%;
  background: #fff;
  font-size: 0.6875rem;
  font-weight: 700;
}

.request-steps__step--done {
  color: $zs-text;

  .request-steps__mark {
    border-color: $primary;
    background: $primary;
    color: #fff;
  }
}

.request-steps__step--current {
  color: $zs-text;
  font-weight: 700;

  .request-steps__mark {
    border-color: $primary;
    color: $primary;
  }
}

// линия между шагами — на уровне середины кружка
.request-steps__line {
  flex: 1;
  height: 0.125rem;
  min-width: 0.5rem;
  margin: 0.6rem -1.4rem 0;
  background: $zs-border;

  &--done {
    background: $primary;
  }
}

.request-steps--large {
  font-size: 0.8125rem;

  .request-steps__mark {
    width: 1.8rem;
    height: 1.8rem;
    font-size: 0.8125rem;
  }

  .request-steps__line {
    margin-top: 0.8rem;
  }
}
</style>
