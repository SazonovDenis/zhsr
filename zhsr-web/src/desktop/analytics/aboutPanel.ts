import { ref } from 'vue'

import { onSessionReset } from '@/shared/sessionState'

/** Открыта ли панель «О диаграмме»: одно состояние на все диаграммы — закрытая не появляется снова у следующей. */
export const aboutOpen = ref(true)

onSessionReset(() => (aboutOpen.value = true))
