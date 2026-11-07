<template>
  <div class="text-photos">
    <div class="text-photos__text" :class="{ 'text-photos__text--open': canOpen }" @click="open">
      <slot />
    </div>
    <div v-if="all.length > 0" class="text-photos__photos">
      <PhotoStrip v-if="photos.length > 0" :photos="photos" :all="all" :caption="caption" small vertical />
      <slot name="photos" :all="all" :caption="caption" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

import type { RequestPhoto } from '@/api/requests'
import { openDialog } from '@/shared/components/dialog/openDialog'
import PhotoStrip from './PhotoStrip.vue'
import PhotoViewer from './PhotoViewer.vue'
import type { PhotoCaption } from './requestView'

/**
 * Текст и фото одной записью: текст (слот default) слева, миниатюры столбиком справа. Так показываются описание заявки,
 * примечания и фото в истории, в том числе ожидающие отправки. photos — фото на сервере; pending — ожидающие отправки,
 * они выводятся в слоте photos. Нажатие на текст или миниатюру открывает просмотр: листаются все фото записи, поверх
 * фото — caption.
 */
const props = withDefaults(
  defineProps<{ photos?: RequestPhoto[]; pending?: RequestPhoto[]; caption?: PhotoCaption | null }>(),
  { photos: () => [], pending: () => [], caption: null },
)

const all = computed(() => [...props.photos, ...props.pending])
const canOpen = computed(() => all.value.some((p) => p.url))

function open() {
  if (!canOpen.value) return
  void openDialog(PhotoViewer, { photos: all.value.filter((p) => p.url), index: 0, caption: props.caption })
}
</script>

<style scoped lang="scss">
.text-photos {
  display: flex;
  gap: 0.8rem;
}

.text-photos__text {
  flex: 1;
  min-width: 0;

  &--open {
    cursor: pointer;
  }
}

.text-photos__photos {
  display: flex;
  flex: none;
  flex-direction: column;
  align-items: flex-end;
  gap: 0.5rem;
}
</style>
