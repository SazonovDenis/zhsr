<template>
  <div class="photo-strip">
    <a
      v-for="(photo, i) in photos"
      :key="photo.id"
      class="photo-strip__photo"
      :class="{ 'photo-strip__photo--small': small }"
      :href="photo.url ?? undefined"
      target="_blank"
      rel="noopener"
    >
      <img v-if="photo.url" :src="photo.url" :alt="t('Фото {n}', { n: i + 1 })" />
      <span v-else>{{ t('Фото {n}', { n: i + 1 }) }}</span>
    </a>
  </div>
</template>

<script setup lang="ts">
import type { RequestPhoto } from '@/api/requests'
import { t } from '@/boot/i18n'

/** Фото заявки или пояснения миниатюрами; клик открывает фото целиком. Фото без файла — заглушка с номером. */
withDefaults(defineProps<{ photos: RequestPhoto[]; small?: boolean }>(), { small: false })
</script>

<style scoped lang="scss">
.photo-strip {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
}

.photo-strip__photo {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 4.5rem;
  height: 4.5rem;
  overflow: hidden;
  border-radius: 0.25rem;
  background: repeating-linear-gradient(45deg, #dfe2e5 0 0.375rem, #eceef0 0.375rem 0.75rem);
  color: $zs-text-muted;
  font-size: 0.6875rem;
  text-decoration: none;

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
