import { defineStore } from '#q-app'
import { createPinia } from 'pinia'

// корневой экземпляр Pinia; сами хранилища лежат в разделах: src/features/<раздел>/store.ts
export default defineStore(() => createPinia())
