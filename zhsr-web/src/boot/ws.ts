import { defineBoot } from '#q-app'

import { dictManager } from '@/shared/dict/DictManager'
import { startServerCheck } from '@/shared/user'
import { connectWs } from '@/shared/ws'

// соединение с сервером, сверка локальной БД при возврате связи и подписка DictManager на команды сервера
export default defineBoot(() => {
  connectWs()
  startServerCheck()
  dictManager.start()
})
