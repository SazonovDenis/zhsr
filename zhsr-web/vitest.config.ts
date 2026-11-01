import { fileURLToPath, URL } from 'node:url'
import vue from '@vitejs/plugin-vue'
import { quasar, transformAssetUrls } from '@quasar/vite-plugin'
import { defineConfig } from 'vitest/config'

// unit-тесты лежат рядом с кодом: src/**/*.spec.ts
export default defineConfig({
  plugins: [vue({ template: { transformAssetUrls } }), quasar({ sassVariables: 'src/css/quasar.variables.scss' })],
  resolve: {
    alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) },
  },
  test: {
    environment: 'happy-dom',
    include: ['src/**/*.spec.ts'],
    passWithNoTests: true,
  },
})
