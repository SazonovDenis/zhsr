import { openDialog } from '@/shared/components/dialog/openDialog'
import { db, LAST_LOGIN } from './db'
import DbChangedDialog from './DbChangedDialog.vue'

/**
 * Сверка локальной БД браузера с серверной БД. У каждой серверной БД свой идентификатор, новый при каждом её
 * пересоздании. Он хранится и в локальной БД: версии справочников, копии данных и очередь заявок относятся только к той
 * серверной БД, от которой получены.
 */

/** Ключ идентификатора серверной БД в таблице meta. */
const DB_GUID = 'dbGuid'

/**
 * Сверить локальную БД с серверной. Идентификатор совпал — ничего не делает. Не совпал или его ещё нет — локальная БД
 * стирается, а приложение открывается заново с главной страницы: в памяти остались данные старой БД, а текущей страницы
 * в новой БД может не быть. В этом случае промис не завершается: вызывающий не должен продолжать работу со старыми
 * данными. Неотправленные заявки отправить некуда: перед стиранием пользователь видит, сколько их.
 */
export async function checkServerDb(dbGuid: string): Promise<void> {
  const stored = await db.meta.get(DB_GUID)
  if (stored?.value === dbGuid) return

  const count = await db.outbox.count()
  if (count > 0) await openDialog(DbChangedDialog, { count })

  // логин последнего вошедшего — не данные серверной БД: он остаётся
  const lastLogin = await db.meta.get(LAST_LOGIN)
  await db.transaction('rw', db.tables, async () => {
    await Promise.all(db.tables.map((table) => table.clear()))
    await db.meta.put({ key: DB_GUID, value: dbGuid })
    if (lastLogin) await db.meta.put(lastLogin)
  })

  location.replace(import.meta.env.QUASAR_VUE_ROUTER_BASE || '/')
  await new Promise<never>(() => {})
}
