<template>
  <q-dialog :model-value="show" maximized @update:model-value="emit('update:show', $event)">
    <div
      class="photo-viewer"
      tabindex="-1"
      autofocus
      @keydown.left="step(-1)"
      @keydown.right="step(1)"
      @pointerdown="onPointerDown"
      @pointerup="onPointerUp"
    >
      <q-carousel
        v-model="current"
        class="photo-viewer__carousel"
        height="100%"
        animated
        :arrows="photos.length > 1"
        control-color="white"
        transition-prev="slide-right"
        transition-next="slide-left"
      >
        <q-carousel-slide
          v-for="(photo, i) in photos"
          :key="photo.id"
          :name="i"
          class="photo-viewer__slide q-pa-none"
          @click="onTap"
        >
          <img
            class="photo-viewer__img"
            :src="photo.url ?? undefined"
            :alt="t('Фото {n}', { n: i + 1 })"
            draggable="false"
          />
        </q-carousel-slide>
      </q-carousel>

      <template v-if="caption && captionShown">
        <div class="photo-viewer__caption photo-viewer__caption--top">{{ caption.meta }}</div>
        <div v-if="caption.text" class="photo-viewer__caption photo-viewer__caption--bottom">{{ caption.text }}</div>
      </template>

      <button
        type="button"
        class="photo-viewer__close q-pa-none"
        :title="t('Закрыть')"
        @click="emit('update:show', false)"
      >
        <q-icon name="close" size="1.5rem" />
      </button>
    </div>
  </q-dialog>
</template>

<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'

import type { RequestPhoto } from '@/api/requests'
import { t } from '@/boot/i18n'
import type { HistoryDialog } from '@/shared/components/dialog/dialogHistory'
import { dialogHistory } from '@/shared/components/dialog/dialogHistory'
import { isMobile } from '@/shared/device'
import type { PhotoCaption } from './requestView'

/**
 * Просмотр фото на весь экран поверх страницы: фото вписано в экран, между фото — стрелки, клавиши ←/→ и свайп, Escape
 * и крестик закрывают. На телефоне кнопка «назад» закрывает просмотр (см. dialogHistory). Открывается через openDialog;
 * в photos — только фото с файлом, index — какое показать первым. caption — текст поверх фото без фона: кто и когда
 * сверху, текст снизу; нажатие на фото скрывает и снова показывает его.
 */
const props = defineProps<{ show?: boolean; photos: RequestPhoto[]; index: number; caption?: PhotoCaption | null }>()

const emit = defineEmits<{ 'update:show': [show: boolean] }>()

const current = ref(props.index)
const captionShown = ref(true)

function step(delta: number) {
  const next = current.value + delta
  if (next >= 0 && next < props.photos.length) current.value = next
}

// свайп — своим обработчиком на весь экран: и пальцем, и мышью, с какого бы места экрана он ни начался
const SWIPE_DISTANCE = 40
let startX = 0
let startY = 0
let swiped = false

function onPointerDown(e: PointerEvent) {
  startX = e.clientX
  startY = e.clientY
  swiped = false
}

function onPointerUp(e: PointerEvent) {
  const dx = e.clientX - startX
  if (Math.abs(dx) < SWIPE_DISTANCE || Math.abs(dx) < Math.abs(e.clientY - startY)) return
  swiped = true
  step(dx < 0 ? 1 : -1)
}

// после свайпа браузер может прислать нажатие: текст поверх фото от него не скрывается
function onTap() {
  if (!swiped) captionShown.value = !captionShown.value
  swiped = false
}

const historyEntry: HistoryDialog = { isShown: () => props.show, close: () => emit('update:show', false) }

// immediate: просмотр создаётся уже показанным
watch(
  () => props.show,
  (show) => {
    if (show) {
      if (isMobile.value) dialogHistory.push(historyEntry)
    } else {
      dialogHistory.pop(historyEntry)
    }
  },
  { immediate: true },
)

onBeforeUnmount(() => dialogHistory.forget(historyEntry))
</script>

<style scoped lang="scss">
.photo-viewer {
  position: relative;
  width: 100%;
  height: 100%;
  background: #000;
  // горизонтальное движение пальца — свайп, браузер его не перехватывает; масштаб двумя пальцами остаётся
  touch-action: pan-y pinch-zoom;
  outline: none;
  user-select: none;
}

.photo-viewer__carousel {
  background: transparent;

  // панель карусели — сама прокручиваемая область: без этого браузер забирает движение пальца по фото себе и обрывает
  // свайп (touch-action от родителя через прокручиваемую область не наследуется)
  :deep(.q-panel) {
    overflow: hidden;
  }

  :deep(.q-panel),
  :deep(.q-carousel__slide),
  :deep(.q-panel-parent),
  img {
    touch-action: pan-y pinch-zoom;
  }
}

.photo-viewer__slide {
  display: flex;
  align-items: center;
  justify-content: center;
}

.photo-viewer__img {
  max-width: 100%;
  max-height: 100%;
  object-fit: contain;
}

.photo-viewer__caption {
  position: absolute;
  right: 0.8rem;
  left: 0.8rem;
  z-index: 1;
  color: #fff;
  font-size: 0.9rem;
  line-height: 1.4;
  text-shadow:
    0 0 0.3rem #000,
    0 0 0.1rem #000;
  white-space: pre-line;
  // нажатие проходит сквозь текст к фото
  pointer-events: none;

  &--top {
    top: calc(0.8rem + env(safe-area-inset-top));
    // справа — крестик
    right: 3.8rem;
    font-size: 0.8rem;
  }

  &--bottom {
    bottom: calc(0.8rem + env(safe-area-inset-bottom));
  }
}

.photo-viewer__close {
  position: absolute;
  top: calc(0.5rem + env(safe-area-inset-top));
  right: 0.5rem;
  z-index: 2;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 2.8rem;
  height: 2.8rem;
  border: 0;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.5);
  color: #fff;
  cursor: pointer;
}
</style>
