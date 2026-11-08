// Сверка переводов: находит в исходниках тексты интерфейса на русском и сообщает, каких нет в kk.json и en.json,
// а также переводы, текст которых в исходниках больше не встречается.
//
// Текст интерфейса — это:
// - любая строка на русском в одинарных кавычках в .ts и .vue: t('…'), колонки таблиц, значения фильтров, подписи
//   быстрых вариантов и месяцев;
// - атрибуты компонентов с русским текстом без привязки (label="…", placeholder="…", title="…" и другие);
// - label в src/desktop/menu/menu.json;
// - тексты справочников, которые переводятся через t() (translate: 'i18n' в src/shared/dict/dicts.ts) — из
//   фиктивных данных src/api/dict.ts;
// - DATA_KEYS — тексты, которые приходят с сервера в данных и переводятся при показе.
// Остальное в src/api не проверяется: это фиктивные данные (названия станций, маршрутов, фамилии), их не переводят.
// src/shared/locale.ts не проверяется: названия языков написаны каждое на своём языке.
//
// Запуск: npm run i18n:check. Код выхода 1 — есть тексты без перевода.

import { readFileSync, readdirSync, statSync } from 'node:fs'
import { join, relative } from 'node:path'

const root = new URL('..', import.meta.url).pathname
const src = join(root, 'src')
const cyr = /[А-Яа-яЁё]/

// тексты из данных сервера, которые показываются через t()
const DATA_KEYS = ['Пассажирский', 'Состав', 'Бригада', 'Неверный логин или пароль']

function files(dir) {
  return readdirSync(dir).flatMap((name) => {
    const path = join(dir, name)
    if (statSync(path).isDirectory()) return name === 'api' || name === 'i18n' ? [] : files(path)
    if (path.endsWith(join('shared', 'locale.ts'))) return []
    return /\.(ts|vue)$/.test(name) ? [path] : []
  })
}

function stripComments(text) {
  return text
    .replace(/\/\*[\s\S]*?\*\//g, '')
    .replace(/<!--[\s\S]*?-->/g, '')
    .replace(/(^|\s)\/\/.*$/gm, '$1')
}

const patterns = [
  /'((?:[^'\\\n]|\\.)*)'/g,
  /(?<![:@\w-])(?:label|placeholder|title|empty|hint|ok-text|date-text|text)="([^"{}]+)"/g,
]

const used = new Map()
function add(key, where) {
  if (!cyr.test(key)) return
  if (!used.has(key)) used.set(key, new Set())
  used.get(key).add(where)
}

for (const path of files(src)) {
  const text = stripComments(readFileSync(path, 'utf8'))
  for (const re of patterns) for (const m of text.matchAll(re)) add(m[1], relative(root, path))
}
// справочники с translate: 'i18n' — их тексты в фиктивных данных
const dictDefs = readFileSync(join(src, 'shared/dict/dicts.ts'), 'utf8')
const i18nDicts = [...dictDefs.matchAll(/(\w+): \{ translate: 'i18n'/g)].map((m) => m[1])
const dictData = readFileSync(join(src, 'api/dict.ts'), 'utf8')
for (const name of i18nDicts) {
  const block = dictData.match(new RegExp(`\\n  ${name}: \\[([\\s\\S]*?)\\n  \\]`))?.[1] ?? ''
  for (const m of block.matchAll(/text: '([^']+)'/g)) add(m[1], 'src/api/dict.ts')
}
for (const key of DATA_KEYS) add(key, 'данные сервера')

const menu = JSON.parse(readFileSync(join(src, 'desktop/menu/menu.json'), 'utf8'))
for (const item of [...menu.items, menu.admin, ...menu.admin.items]) add(item.label, 'src/desktop/menu/menu.json')

let missing = 0
for (const lang of ['kk', 'en']) {
  const messages = JSON.parse(readFileSync(join(src, `i18n/${lang}.json`), 'utf8'))
  const absent = [...used.keys()].filter((key) => !(key in messages))
  const unused = Object.keys(messages).filter((key) => !used.has(key))
  missing += absent.length
  if (absent.length) {
    console.log(`\n${lang}.json — нет перевода (${absent.length}):`)
    for (const key of absent) console.log(`  ${JSON.stringify(key)}  ← ${[...used.get(key)].join(', ')}`)
  }
  if (unused.length) {
    console.log(
      `\n${lang}.json — текст не найден в исходниках (${unused.length}), возможно, он изменился или его дают данные:`,
    )
    for (const key of unused) console.log(`  ${JSON.stringify(key)}`)
  }
}
console.log(missing ? `\nНе переведено: ${missing}` : '\nВсе тексты интерфейса переведены')
process.exit(missing ? 1 : 0)
