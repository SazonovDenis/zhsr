import { register } from 'register-service-worker'

// service worker управляет только мобильной частью (/m/); десктоп и администрирование работают без него
register(import.meta.env.QUASAR_SERVICE_WORKER_FILE, {
  registrationOptions: { scope: '/m/' },
})
