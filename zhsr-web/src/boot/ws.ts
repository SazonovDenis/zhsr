import { defineBoot } from '#q-app'

import { dictManager } from '@/shared/dict/DictManager'
import { connectWs } from '@/shared/ws'

// соединение с сервером и подписка DictManager на команды обновления справочников
export default defineBoot(() => {
  connectWs()
  dictManager.start()
})
