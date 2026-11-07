<template>
  <div class="photo-strip" :class="{ 'photo-strip--vertical': vertical }">
    <button
      v-for="(photo, i) in photos"
      :key="photo.id"
      type="button"
      class="photo-strip__photo q-pa-none"
      :class="{ 'photo-strip__photo--small': small }"
      :disabled="!photo.url"
      @click="view(photo)"
    >
      <img v-if="photo.url" :src="photo.url" :alt="t('Фото {n}', { n: i + 1 })" />
      <span v-else>{{ t('Фото {n}', { n: i + 1 }) }}</span>
    </button>
  </div>
</template>

<script setup lang="ts">
import type { RequestPhoto } from '@/api/requests'
import { t } from '@/boot/i18n'
import { openDialog } from '@/shared/components/dialog/openDialog'
import PhotoViewer from './PhotoViewer.vue'
import type { PhotoCaption } from './requestView'

/**
 * Фото заявки или примечания миниатюрами; клик открывает просмотр на весь экран. Фото без файла — заглушка. vertical —
 * столбиком.
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
}
</style>
