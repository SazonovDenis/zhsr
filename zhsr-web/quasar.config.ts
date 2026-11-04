// Конфигурация Quasar CLI: https://v2.quasar.dev/quasar-cli-vite/quasar-config-file

import { defineConfig } from '#q-app'

export default defineConfig((ctx) => {
  return {
    // загрузочные файлы из src/boot, выполняются до создания приложения
    boot: ['i18n', 'errors', 'ws'],

    css: ['app.scss'],

    // шрифты макета подключаются в index.html; material-icons нужны встроенным иконкам компонентов Quasar
    extras: ['material-icons'],

    build: {
      target: {},

      typescript: {
        strict: true,
        vueShim: true,
      },

      vueRouterMode: 'history',

      vitePlugins: [
        [
          '@intlify/unplugin-vue-i18n/vite',
          {
            ssr: ctx.mode.ssr || ctx.mode.ssg,
            include: [ctx.appPaths.resolve.app('src/i18n')],
          },
        ],
        [
          'vite-plugin-checker',
          {
            vueTsc: true,
            eslint: {
              lintCommand: 'eslint -c ./eslint.config.js "./src*/**/*.{ts,js,mjs,cjs,vue}"',
              useFlatConfig: true,
            },
          },
          { server: false },
        ],
      ],
    },

    devServer: {
      port: 9999,
      open: false,
      // запросы /api уходят на сервер zhsr-app; у контроллеров сервера префикса /api нет, поэтому он отрезается
      proxy: {
        '/api': {
          target: 'http://localhost:8080',
          changeOrigin: true,
          // через этот же прокси идёт WebSocket /api/ws (src/shared/ws.ts)
          ws: true,
          rewrite: (path: string) => path.replace(/^\/api/, ''),
        },
      },
    },

    framework: {
      config: {},
      lang: 'ru',
      plugins: ['Notify', 'Dialog', 'Loading'],
    },

    animations: [],

    // мобильная часть (/m/) — PWA; scope и start_url заданы в src-pwa/manifest.json, scope service worker — в src-pwa/register-sw.ts
    pwa: {
      workboxMode: 'GenerateSW',
      extendPWAGenerateSWOptions() {
        if (!ctx.prod) return
        return {
          // в кэш service worker не попадают чанки десктопа и администрирования
          globIgnores: ['**/assets/desktop-*', '**/assets/admin-*'],
          // без сети открываются только страницы мобильной части
          navigateFallbackAllowlist: [/^\/m(\/|$)/],
        }
      },
    },
  }
})
