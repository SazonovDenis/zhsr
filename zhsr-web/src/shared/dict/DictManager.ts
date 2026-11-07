import { reactive } from 'vue'

import { loadDict, loadDictValue } from '@/api/dict'
import { isNetworkError } from '@/api/http'
import { i18n, t } from '@/boot/i18n'
import { bus } from '@/shared/bus'
import type { ShownError } from '@/shared/errors'
import { shownError } from '@/shared/errors'
import { db } from '@/shared/db/db'
import { ROUTE_KIND_RECORDS } from '@/shared/constants/RouteKind'
import { SYSTEM_DICT_RECORDS } from '@/shared/constants/systemDicts'
import { loadCurrentUser } from '@/shared/user'
import { subscribe } from '@/shared/ws'
import { DICT_COLORS } from './dictColors'
import type { DictData, DictDef, DictId, DictName, DictRec, MultiText } from './dicts'
import { dictDefs } from './dicts'

/**
 * Загрузчик справочников и их кэш. Один экземпляр на приложение: dictManager.
 *
 * Работа со справочником — явно, грузит прикладной код, менеджер при чтении ничего не грузит:
 * 1. загрузить — await dictManager.prepareDict('Depot'): фрейм грузит справочники своего главного показывающего в своей
 *    цепочке, контрол выбора — свой справочник (dictSource); справочник загружается один раз, параллельные запросы дают
 *    один запрос к серверу;
 * 2. показать — dictManager.getValue('Depot', id): текст записи на текущем языке (см. translate в dicts.ts). Справочник
 *    не загружен или записи нет — «<<id>>»: бросается в глаза, а в консоли — какой справочник не загрузили.
 *
 * Обновление:
 * - refreshDict(name) — перезагрузить справочник целиком, refreshValue(name, id) — одну запись,
 *   refreshAll() — все загруженные;
 * - сервер шлёт эти команды в канал WebSocket `dict-manager` (например, после правки в администрировании);
 * - версии всех справочников приходят вместе с текущим пользователем (событие шины `dict-versions`): менеджер
 *   перезагружает только изменившиеся, так подхватываются и команды сервера, пропавшие за время разрыва связи;
 * - после обновления в шину уходит `dict-refresh`.
 *
 * Реактивность: getValue, getRec и getList читают реактивный счётчик справочника, поэтому компоненты, которые
 * показывали значения справочника, перерисовываются сами после его загрузки и обновления. Событие `dict-refresh`
 * нужно для логики, не связанной с отрисовкой.
 *
 * Справочники с признаком offline хранятся и в IndexedDB: без связи они берутся оттуда, при связи копия сверяется
 * с сервером по версии до показа. Ошибка сервера не прячется за копией. Версии — простая нумерация внутри одной
 * серверной БД: копии от другой БД не доходят до сверки, их стирает checkServerDb.
 *
 * Системные справочники (system в dicts.ts) с сервера не загружаются и не обновляются: записи — из кода
 * (shared/constants), оформление — из dictColors.ts.
 */

/** Состояние загрузки справочника: идёт ли загрузка и ошибка последней загрузки (null — нет). */
export interface DictLoadState {
  loading: boolean
  error: ShownError | null
}

/** Команда сервера в канале `dict-manager`. */
type DictCommand =
  { cmd: 'refreshDict'; dict: DictName } | { cmd: 'refreshValue'; dict: DictName; id: DictId } | { cmd: 'refreshAll' }

class Dict {
  readonly map = new Map<DictId, DictRec>()

  constructor(
    readonly name: DictName,
    public version: string,
    public list: DictRec[],
  ) {
    for (const rec of list) this.map.set(rec.id, rec)
  }

  put(rec: DictRec) {
    const index = this.list.findIndex((r) => r.id === rec.id)
    if (index === -1) this.list = [...this.list, rec]
    else this.list = this.list.map((r) => (r.id === rec.id ? rec : r))
    this.map.set(rec.id, rec)
  }

  remove(id: DictId) {
    this.list = this.list.filter((r) => r.id !== id)
    this.map.delete(id)
  }
}

class DictManager {
  private readonly dicts = new Map<DictName, Dict>()

  // идущие загрузки: параллельные запросы одного справочника ждут одну загрузку
  private readonly loading = new Map<DictName, Promise<Dict>>()

  // счётчик изменений справочника: его читают getValue, getRec и getList, поэтому компоненты перерисовываются
  private readonly ticks = reactive<Partial<Record<DictName, number>>>({})

  // состояние загрузки справочников: для показа в выпадающих списках
  private readonly states = reactive<Partial<Record<DictName, DictLoadState>>>({})

  /** Подписка на команды сервера и загрузка справочников, нужных без сети. Вызывается один раз (src/boot/ws.ts). */
  start() {
    subscribe('dict-manager', (body) => void this.onCommand(body as DictCommand))
    bus.on('dict-versions', (versions) => void this.refreshChanged(versions))

    for (const name of Object.keys(dictDefs) as DictName[]) {
      if (this.isOffline(name) && !this.isSystem(name)) void this.prepareDict(name)
    }
  }

  // --- чтение ---

  /**
   * Текст поля записи на текущем языке. Справочник не загружен или записи нет — «<<id>>»: бросается в глаза, что
   * значение не показано; причина пишется в консоль.
   */
  getValue(name: DictName, id: DictId | null | undefined, field = 'text'): string {
    if (id === null || id === undefined) return ''
    const rec = this.getRec(name, id)
    if (!rec) return `<<${id}>>`
    const value = rec[field]
    if (value === null || value === undefined) {
      console.warn(`Dict value is empty, dict: ${name}, id: ${id}, field: ${field}`)
      return `<<${id}>>`
    }
    return this.translate(name, value)
  }

  getRec(name: DictName, id: DictId): DictRec | null {
    this.track(name)
    const dict = this.dicts.get(name)
    if (!dict) {
      // справочник не загрузили: прикладной код должен загрузить его до показа (prepareDict)
      console.warn(`Dict not loaded: ${name}`)
      return null
    }
    const rec = dict.map.get(id)
    if (!rec) {
      console.warn(`Dict rec not found, dict: ${name}, id: ${id}`)
      return null
    }
    return rec
  }

  /** Все записи справочника; справочник не загружен — пустой список. */
  getList(name: DictName): DictRec[] {
    this.track(name)
    return this.dicts.get(name)?.list ?? []
  }

  // --- загрузка ---

  /** Загрузить справочник, если он ещё не загружен. */
  async prepareDict(name: DictName): Promise<void> {
    if (this.dicts.has(name)) return
    if (this.isSystem(name)) {
      this.dicts.set(name, systemDict(name))
      this.changed(name)
      return
    }
    await this.load(name, () => this.loadInitial(name))
  }

  async prepareDicts(...names: DictName[]): Promise<void> {
    await Promise.all(names.map((name) => this.prepareDict(name)))
  }

  // --- обновление ---

  /** Перезагрузить справочник целиком. Справочник, который ещё не загружался, загрузится при первом обращении. */
  async refreshDict(name: DictName): Promise<void> {
    // идёт загрузка — дожидаемся её, затем перезагружаем: она могла начаться до изменения на сервере
    await this.loading.get(name)
    if (!this.dicts.has(name) || this.isSystem(name)) return
    await this.load(name, async () => this.save(name, await loadDict(name)))
  }

  /** Обновить одну запись справочника; сервер ответил, что записи нет, — она удаляется. */
  async refreshValue(name: DictName, id: DictId): Promise<void> {
    // идёт полная загрузка — сначала она, иначе она затёрла бы свежую запись
    await this.loading.get(name)
    const dict = this.dicts.get(name)
    if (!dict || this.isSystem(name)) return

    const { version, record } = await loadDictValue(name, id)
    if (record) dict.put(record)
    else dict.remove(id)
    dict.version = version

    if (this.isOffline(name)) {
      await db.transaction('rw', db.dicts, db.dictRecords, async () => {
        if (record) await db.dictRecords.put({ dict: name, id, rec: record })
        else await db.dictRecords.delete([name, id])
        await db.dicts.put({ name, version, savedAt: Date.now() })
      })
    }

    this.changed(name, id)
  }

  /** Перезагрузить все загруженные справочники. */
  async refreshAll(): Promise<void> {
    await Promise.all([...this.dicts.keys()].map((name) => this.refreshDict(name)))
  }

  // --- внутреннее ---

  /** Перезагрузить загруженные справочники, версия которых отличается от версии на сервере. */
  private async refreshChanged(versions: Partial<Record<DictName, string>>) {
    const stale = [...this.dicts.keys()].filter(
      (name) =>
        !this.isSystem(name) && versions[name] !== undefined && versions[name] !== this.dicts.get(name)?.version,
    )
    await Promise.all(stale.map((name) => this.refreshDict(name)))
  }

  private async onCommand(command: DictCommand) {
    switch (command.cmd) {
      case 'refreshDict':
        return this.refreshDict(command.dict)
      case 'refreshValue':
        return this.refreshValue(command.dict, command.id)
      case 'refreshAll':
        return this.refreshAll()
    }
  }

  /**
   * Первая загрузка. Справочник offline: копия из IndexedDB берётся, только если связи нет; при связи версия копии
   * сверяется с сервером (совпала — копия, нет — справочник с сервера). Ошибка сервера не прячется за копией: она
   * уходит тому, кто загружает справочник, и показывается у него.
   */
  private async loadInitial(name: DictName): Promise<Dict> {
    if (this.isOffline(name)) {
      const stored = await this.readStored(name)
      if (stored) {
        if (!navigator.onLine) return stored
        try {
          const { dictVersions } = await loadCurrentUser()
          if (dictVersions[name] === stored.version) return stored
        } catch (e) {
          if (isNetworkError(e)) return stored
          throw e
        }
      }
    }
    return this.save(name, await loadDict(name))
  }

  /** Загрузка с общим ожиданием: параллельные запросы получают один промис. */
  private async load(name: DictName, loader: () => Promise<Dict>): Promise<Dict> {
    const running = this.loading.get(name)
    if (running) return running

    const promise = loader()
    this.loading.set(name, promise)
    this.states[name] = { loading: true, error: null }
    // отметку снимаем в любом случае: после неудачи справочник можно запросить заново; ошибку получают все ждущие
    try {
      const dict = await promise
      this.dicts.set(name, dict)
      this.states[name] = { loading: false, error: null }
      this.changed(name)
      return dict
    } catch (e) {
      // ошибку получает тот, кто загружает справочник, и она же видна в контролах выбора справочника (dictSource)
      this.states[name] = { loading: false, error: shownError(e) }
      throw e
    } finally {
      this.loading.delete(name)
    }
  }

  /**
   * Состояние загрузки справочника — реактивно: идёт ли загрузка и ошибка последней загрузки (null — нет). Его
   * показывают контролы выбора справочника вместо вариантов (dictSource).
   */
  loadState(name: DictName): DictLoadState {
    return this.states[name] ?? { loading: false, error: null }
  }

  /** Справочник с сервера: в память, а справочник offline — ещё и в IndexedDB. */
  private async save(name: DictName, data: DictData): Promise<Dict> {
    if (this.isOffline(name)) {
      await db.transaction('rw', db.dicts, db.dictRecords, async () => {
        await db.dictRecords.where('dict').equals(name).delete()
        await db.dictRecords.bulkPut(data.records.map((rec) => ({ dict: name, id: rec.id, rec })))
        await db.dicts.put({ name, version: data.version, savedAt: Date.now() })
      })
    }
    return new Dict(name, data.version, data.records)
  }

  private async readStored(name: DictName): Promise<Dict | null> {
    const meta = await db.dicts.get(name)
    if (!meta) return null
    const rows = await db.dictRecords.where('dict').equals(name).toArray()
    return new Dict(
      name,
      meta.version,
      rows.map((row) => row.rec as DictRec),
    )
  }

  private isSystem(name: DictName): boolean {
    return (dictDefs[name] as DictDef).system === true
  }

  private isOffline(name: DictName): boolean {
    return (dictDefs[name] as DictDef).offline === true
  }

  private translate(name: DictName, value: unknown): string {
    switch ((dictDefs[name] as DictDef).translate) {
      case 'i18n':
        return t(String(value))
      case 'multilang': {
        const texts = value as MultiText
        const locale = i18n.global.locale.value
        return texts[locale] ?? texts.ru ?? Object.values(texts)[0] ?? ''
      }
      case 'none':
        return String(value)
    }
  }

  private track(name: DictName) {
    // системный справочник — в коде: доступен сразу, без prepareDict
    if (!this.dicts.has(name) && this.isSystem(name)) this.dicts.set(name, systemDict(name))
    void this.ticks[name]
  }

  private changed(name: DictName, id?: DictId) {
    this.ticks[name] = (this.ticks[name] ?? 0) + 1
    bus.emit('dict-refresh', id === undefined ? { dictName: name } : { dictName: name, id })
  }
}

/** Системный справочник из кода: записи и их оформление (dictColors.ts). */
function systemDict(name: DictName): Dict {
  const source: Partial<Record<DictName, readonly DictRec[]>> = {
    ...SYSTEM_DICT_RECORDS,
    RouteKind: ROUTE_KIND_RECORDS,
  }
  const records = source[name]
  if (!records) throw new Error(`System dict has no records: ${name}`)
  const colors = DICT_COLORS[name] ?? {}
  return new Dict(
    name,
    'system',
    records.map((rec) => ({ ...rec, ...colors[rec.id] })),
  )
}

export const dictManager = new DictManager()
