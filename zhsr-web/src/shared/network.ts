import { ref } from 'vue'

/**
 * Есть ли связь — по событиям браузера online и offline. Показывается в шапке мобильной части; по нему же действия,
 * которым нужен сервер, становятся недоступны.
 */
export const isOnline = ref(navigator.onLine)

window.addEventListener('online', () => (isOnline.value = true))
window.addEventListener('offline', () => (isOnline.value = false))
