import { defineConfig, devices } from '@playwright/test'

// e2e-тесты лежат в e2e/; приложение запускается на dev-сервере Quasar
export default defineConfig({
  testDir: './e2e',
  use: {
    baseURL: 'http://localhost:9000',
    trace: 'on-first-retry',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: {
    command: 'npm run dev',
    url: 'http://localhost:9000',
    reuseExistingServer: true,
  },
})
