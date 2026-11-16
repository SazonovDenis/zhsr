<template>
  <div class="photo-strip" :class="{ 'photo-strip--vertical': vertical }">
    <div v-for="(photo, i) in photos" :key="photo.id" class="photo-strip__item">
      <button
        type="button"
        class="photo-strip__photo q-pa-none"
        :class="{ 'photo-strip__photo--small': small, 'photo-strip__photo--desktop': small && !isMobile }"
        :disabled="!photo.url"
        @click="view(photo)"
      >
        <img v-if="photo.url" :src="photo.url" :alt="t('Фото {n}', { n: i + 1 })" />
        <span v-else>{{ t('Фото {n}', { n: i + 1 }) }}</span>
      </button>
      <!-- удалить своё фото: первое нажатие спрашивает подтверждение, второе удаляет -->
      <button
        v-if="changes?.canDeletePhoto(photo)"
        type="button"
        class="photo-strip__delete q-pa-none"
        :class="{ 'photo-strip__delete--confirm': confirming === photo.id }"
        :title="t(confirming === photo.id ? 'Нажмите ещё раз, чтобы удалить' : 'Удалить фото')"
        @click="remove(photo)"
        @blur="confirming = null"
        @mouseleave="confirming = null"
      >
        ✕
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'

import type { RequestPhoto } from '@/api/requests'
import { t } from '@/boot/i18n'
import { isMobile } from '@/shared/device'
import { openDialog } from '@/shared/components/dialog/openDialog'
import PhotoViewer from './PhotoViewer.vue'
import { useRequestChanges } from './requestChanges'
import type { PhotoCaption } from './requestView'

/**
 * Фото заявки или примечания миниатюрами; клик открывает просмотр на весь экран. Фото без файла — заглушка. vertical —
 * столбиком. small — мелкие миниатюры; на десктопе они вдвое крупнее, чем на телефоне. На своём фото автор видит
 * крестик удаления, пока не вышел срок (requestChanges.ts).
 */
const props = withDefaults(
  defineProps<{
    photos: RequestPhoto[]
    small?: boolean
    vertical?: boolean
    /** все фото для листания в просмотре, если здесь показана только часть */
    all?: RequestPhoto[] | null
    /** текст поверх фото в просмотре */
    caption?: PhotoCaption | null
  }>(),
  { small: false, vertical: false, all: null, caption: null },
)

const changes = useRequestChanges()

/** фото, удаление которого ждёт подтверждения вторым нажатием; null — нет */
const confirming = ref<string | null>(null)

function remove(photo: RequestPhoto) {
  if (confirming.value !== photo.id) {
    confirming.value = photo.id
    return
  }
  confirming.value = null
  changes?.deletePhoto(photo)
}

function view(photo: RequestPhoto) {
  const photos = (props.all ?? props.photos).filter((p) => p.url)
  const index = photos.findIndex((p) => p.id === photo.id)
  void openDialog(PhotoViewer, { photos, index, caption: props.caption })
}
</script>

<style scoped lang="scss">
.photo-strip {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;

  &--vertical {
    flex-direction: column;
    flex-wrap: nowrap;
  }
}

.photo-strip__item {
  position: relative;
}

.photo-strip__delete {
  position: absolute;
  top: 0.2rem;
  right: 0.2rem;
  width: 1.2rem;
  height: 1.2rem;
  border: 0;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.55);
  color: #fff;
  font-size: 0.6rem;
  cursor: pointer;

  &--confirm {
    background: #b91c1c;
  }
}

.photo-strip__photo {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 4.5rem;
  height: 4.5rem;
  overflow: hidden;
  border: 0;
  border-radius: 0.3rem;
  background: repeating-linear-gradient(45deg, #dfe2e5 0 0.4rem, #eceef0 0.4rem 0.8rem);
  color: $zs-text-muted;
  font-size: 0.7rem;
  cursor: pointer;

  &:disabled {
    cursor: default;
  }

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }

  &--small {
    width: 3rem;
    height: 3rem;
  }

  // десктоп: места больше — миниатюры вдвое крупнее
  &--desktop {
    width: 6rem;
    height: 6rem;
  }
}
</style>
