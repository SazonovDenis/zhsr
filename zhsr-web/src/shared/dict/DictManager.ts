import { reactive } from 'vue'

import { loadDict, loadDictValue, loadDictVersions } from '@/api/dict'
import { i18n, t } from '@/boot/i18n'
import { bus } from '@/shared/bus'
import { db } from '@/shared/db/db'
import { subscribe } from '@/shared/ws'
import type { DictData, DictDef, DictId, DictName, DictRec, MultiText } from './dicts'
import { dictDefs } from './dicts'

/**
 * Загрузчик справочников и их кэш. Один экземпляр на приложение: dictManager.
 *
 * Работа со справочником:
 * 1. перед показом — await dictManager.prepareDict('WagonState'): справочник загружается один раз, параллельные
 *    запросы одного справочника дают один запрос к серверу;
 * 2. при показе — dictManager.getValue('WagonState', id): текст записи на текущем языке (см. translate в dicts.ts).
 *
 * Обновление:
 * - refreshDict(name) — перезагрузить справочник целиком, refreshValue(name, id) — одну запись,
 *   refreshAll() — все загруженные, refreshStale() — сверить версии с сервером и перезагрузить только изменившиеся;
 * - сервер шлёт эти команды в канал WebSocket `dict-manager` (например, после правки в администрировании);
 * - после разрыва связи (событие шины `connection-restored`) менеджер сам вызывает refreshStale: команды сервера за
 *   время разрыва могли пропасть;
 * - после обновления в шину уходит `dict-refresh`.
 *
 * Реактивность: getValue, getRec и getList читают реактивный счётчик справочника, поэтому компоненты, которые
 * показывали значения справочника, перерисовываются сами после его загрузки и обновления. Событие `dict-refresh`
 * нужно для логики, не связанной с отрисовкой.
 *
 * Справочники с признаком offline хранятся и в IndexedDB: при старте они берутся оттуда, а при наличии сети
 * сверяются с сервером по версии.
 */

/** Команда сервера в канале `dict-manager`. */
type DictCommand =
  | { cmd: 'refreshDict'; dict: DictName }
  | { cmd: 'refreshValue'; dict: DictName; id: DictId }
  | { cmd: 'refreshAll' }
  | { cmd: 'refreshStale' }

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

  private staleCheck: Promise<void> | null = null

  /** Подписка на команды сервера и загрузка справочников, нужных без сети. Вызывается один раз (src/boot/ws.ts). */
  start() {
    subscribe('dict-manager', (body) => void this.onCommand(body as DictCommand))
    bus.on('connection-restored', () => void this.refreshStale())

    for (const name of Object.keys(dictDefs) as DictName[]) {
      if (this.isOffline(name)) void this.prepareDict(name)
    }
  }

  // --- чтение ---

  /**
   * Текст поля записи на текущем языке. Справочник не загружен или записи нет — возвращается id, чтобы интерфейс
   * показал хоть что-то; причина пишется в консоль.
   */
  getValue(name: DictName, id: DictId | null | undefined, field = 'text'): string {
    if (id === null || id === undefined) return ''
    const rec = this.getRec(name, id)
    if (!rec) return String(id)
    const value = rec[field]
    if (value === null || value === undefined) {
      console.warn(`Dict value is empty, dict: ${name}, id: ${id}, field: ${field}`)
      return String(id)
    }
    return this.translate(name, value)
  }

  getRec(name: DictName, id: DictId): DictRec | null {
    this.track(name)
    const dict = this.dicts.get(name)
    if (!dict) {
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
    if (!this.dicts.has(name)) return
    await this.load(name, async () => this.save(name, await loadDict(name)))
  }

  /** Обновить одну запись справочника; сервер ответил, что записи нет, — она удаляется. */
  async refreshValue(name: DictName, id: DictId): Promise<void> {
    // идёт полная загрузка — сначала она, иначе она затёрла бы свежую запись
    await this.loading.get(name)
    const dict = this.dicts.get(name)
    if (!dict) return

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

  /** Сверить версии загруженных справочников с сервером и перезагрузить изменившиеся. */
  refreshStale(): Promise<void> {
    // связь восстановилась сразу по двум признакам — сверка всё равно одна
    if (!this.staleCheck) {
      this.staleCheck = this.checkStale().finally(() => (this.staleCheck = null))
    }
    return this.staleCheck
  }

  // --- внутреннее ---

  private async checkStale() {
    const names = [...this.dicts.keys()]
    if (names.length === 0) return
    const versions = await loadDictVersions(names)
    const stale = names.filter(
      (name) => versions[name] !== undefined && versions[name] !== this.dicts.get(name)?.version,
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
      case 'refreshStale':
        return this.refreshStale()
    }
  }

  /** Первая загрузка: справочник offline берётся из IndexedDB и сверяется с сервером, остальные — с сервера. */
  private async loadInitial(name: DictName): Promise<Dict> {
    if (this.isOffline(name)) {
      const stored = await this.readStored(name)
      if (stored) {
        if (navigator.onLine) void this.revalidate(name, stored.version)
        return stored
      }
    }
    return this.save(name, await loadDict(name))
  }

  /** Сверка версии справочника, взятого из IndexedDB. */
  private async revalidate(name: DictName, version: string) {
    const versions = await loadDictVersions([name])
    if (versions[name] !== undefined && versions[name] !== version) await this.refreshDict(name)
  }

  /** Загрузка с общим ожиданием: параллельные запросы получают один промис. */
  private async load(name: DictName, loader: () => Promise<Dict>): Promise<Dict> {
    const running = this.loading.get(name)
    if (running) return running

    const promise = loader()
    this.loading.set(name, promise)
    // отметку снимаем в любом случае: после неудачи справочник можно запросить заново; ошибку получают все ждущие
    try {
      const dict = await promise
      this.dicts.set(name, dict)
      this.changed(name)
      return dict
    } finally {
      this.loading.delete(name)
    }
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
    void this.ticks[name]
  }

  private changed(name: DictName, id?: DictId) {
    this.ticks[name] = (this.ticks[name] ?? 0) + 1
    bus.emit('dict-refresh', id === undefined ? { dictName: name } : { dictName: name, id })
  }
}

export const dictManager = new DictManager()
