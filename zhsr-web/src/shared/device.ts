import { ref } from 'vue'

// Мобильный режим: сенсорный экран без наведения мышью — телефон, планшет или эмуляция устройства в DevTools.
// Медиазапрос меняется на лету, поэтому приложение перестраивается при включении и выключении эмуляции без перезагрузки.
const query = window.matchMedia('(pointer: coarse) and (hover: none)')

export const isMobile = ref(query.matches)

query.addEventListener('change', (e) => {
  isMobile.value = e.matches
})
