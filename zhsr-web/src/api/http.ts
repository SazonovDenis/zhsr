import axios from 'axios'

// HTTP-клиент сервера zhsr-app; в dev-режиме /api проксируется (quasar.config.ts > devServer.proxy)
export const http = axios.create({ baseURL: '/api' })
