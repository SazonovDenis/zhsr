/* Общие функции дашбордов doc-dashboard1: случайные числа, форматы, шкалы, подсказка, карточка, тема. */
'use strict';

/* ---------- Случайные числа (детерминированные) ---------- */
function mulberry32(a) {
  return function () {
    a |= 0; a = a + 0x6D2B79F5 | 0;
    let t = Math.imul(a ^ a >>> 15, 1 | a);
    t = t + Math.imul(t ^ t >>> 7, 61 | t) ^ t;
    return ((t ^ t >>> 14) >>> 0) / 4294967296;
  };
}
function makeRnd(seed) {
  const r = mulberry32(seed);
  const R = () => r();
  R.between = (a, b) => a + r() * (b - a);
  R.int = (a, b) => Math.floor(a + r() * (b - a + 1));
  R.chance = p => r() < p;
  R.pick = (arr, w) => {
    if (!w) return arr[Math.floor(r() * arr.length)];
    let s = 0; for (const x of w) s += x;
    let v = r() * s;
    for (let i = 0; i < arr.length; i++) { v -= w[i]; if (v <= 0) return arr[i]; }
    return arr[arr.length - 1];
  };
  R.normal = (m = 0, sd = 1) => m + sd * Math.sqrt(-2 * Math.log(1 - r())) * Math.cos(2 * Math.PI * r());
  R.logn = (median, spread) => median * Math.exp(R.normal(0, spread));
  R.exp = mean => -Math.log(1 - r()) * mean;
  R.poisson = lambda => {
    if (lambda <= 0) return 0;
    let L = Math.exp(-lambda), k = 0, p = 1;
    do { k++; p *= r(); } while (p > L);
    return k - 1;
  };
  return R;
}

/* ---------- Время: все отметки - местное время Казахстана, хранится как UTC-мс ---------- */
const MIN = 6e4, HOUR = 36e5, DAY = 864e5;
const NOW = Date.UTC(2026, 10, 22, 8, 0);
const TODAY = Math.floor(NOW / DAY) * DAY;
const MONTHS = ['янв', 'фев', 'мар', 'апр', 'май', 'июн', 'июл', 'авг', 'сен', 'окт', 'ноя', 'дек'];
const MONTHS_FULL = ['январь', 'февраль', 'март', 'апрель', 'май', 'июнь', 'июль', 'август', 'сентябрь', 'октябрь', 'ноябрь', 'декабрь'];
const WD = ['пн', 'вт', 'ср', 'чт', 'пт', 'сб', 'вс'];
const dd = t => { const d = new Date(t); return String(d.getUTCDate()).padStart(2, '0') + '.' + String(d.getUTCMonth() + 1).padStart(2, '0'); };
const ddy = t => dd(t) + '.' + new Date(t).getUTCFullYear();
const hhmm = t => { const d = new Date(t); return String(d.getUTCHours()).padStart(2, '0') + ':' + String(d.getUTCMinutes()).padStart(2, '0'); };
const wday = t => (new Date(t).getUTCDay() + 6) % 7;
const hourOf = t => new Date(t).getUTCHours();
const monthOf = t => new Date(t).getUTCMonth();

/* ---------- Форматы ---------- */
const esc = s => String(s).replace(/[&<>"]/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));
const fmt = (n, d = 0) => Number(n).toLocaleString('ru-RU', { minimumFractionDigits: d, maximumFractionDigits: d });
const pct = (n, d = 0) => fmt(n * 100, d) + '%';
const dur = h => h < 1 ? fmt(h * 60) + ' мин' : h < 48 ? fmt(h, h < 10 ? 1 : 0) + ' ч' : fmt(h / 24, 1) + ' сут';
const plural = (n, a, b, c) => { const m = n % 10, h = n % 100; return m === 1 && h !== 11 ? a : m >= 2 && m <= 4 && (h < 12 || h > 14) ? b : c; };

/* ---------- Статистика ---------- */
const sum = a => a.reduce((s, x) => s + x, 0);
const mean = a => a.length ? sum(a) / a.length : 0;
function quantile(a, q) {
  if (!a.length) return 0;
  const s = [...a].sort((x, y) => x - y), i = (s.length - 1) * q, lo = Math.floor(i), hi = Math.ceil(i);
  return s[lo] + (s[hi] - s[lo]) * (i - lo);
}
const median = a => quantile(a, 0.5);
const groupBy = (a, f) => { const m = new Map(); for (const x of a) { const k = f(x); if (!m.has(k)) m.set(k, []); m.get(k).push(x); } return m; };
const clamp = (v, a, b) => Math.max(a, Math.min(b, v));

/* ---------- Шкалы ---------- */
function lin(d0, d1, r0, r1) {
  const f = v => r0 + (v - d0) / (d1 - d0 || 1) * (r1 - r0);
  f.inv = p => d0 + (p - r0) / (r1 - r0 || 1) * (d1 - d0);
  return f;
}
function logs(d0, d1, r0, r1) {
  const a = Math.log(d0), b = Math.log(d1);
  return v => r0 + (Math.log(Math.max(v, d0)) - a) / (b - a) * (r1 - r0);
}
function niceTicks(a, b, n = 5) {
  const span = b - a || 1, step0 = span / n, mag = Math.pow(10, Math.floor(Math.log10(step0)));
  const err = step0 / mag, step = (err >= 7 ? 10 : err >= 3 ? 5 : err >= 1.5 ? 2 : 1) * mag;
  const out = [];
  for (let v = Math.ceil(a / step) * step; v <= b + step * 1e-9; v += step) out.push(+v.toFixed(10));
  return out;
}
const niceMax = (v, n = 5) => { const t = niceTicks(0, v, n); return t[t.length - 1] < v ? t[t.length - 1] + (t[1] - t[0]) : t[t.length - 1]; };

/* Ступени последовательной шкалы: один цвет, от светлого к тёмному */
const BLUE = ['var(--b100)', 'var(--b200)', 'var(--b300)', 'var(--b400)', 'var(--b500)', 'var(--b600)', 'var(--b700)'];
const ORANGE = ['var(--o100)', 'var(--o200)', 'var(--o300)', 'var(--o400)', 'var(--o500)', 'var(--o600)', 'var(--o700)'];
const step = (ramp, v, max) => ramp[clamp(Math.floor(v / max * ramp.length), 0, ramp.length - 1)];
const SER = ['var(--s1)', 'var(--s2)', 'var(--s3)', 'var(--s4)', 'var(--s5)', 'var(--s6)', 'var(--s7)', 'var(--s8)'];

/* ---------- SVG ---------- */
const P = n => +n.toFixed(1);
const pathOf = pts => pts.map((p, i) => (i ? 'L' : 'M') + P(p[0]) + ',' + P(p[1])).join('');
function smooth(pts) {
  if (pts.length < 3) return pathOf(pts);
  let d = 'M' + P(pts[0][0]) + ',' + P(pts[0][1]);
  for (let i = 0; i < pts.length - 1; i++) {
    const p0 = pts[i - 1] || pts[i], p1 = pts[i], p2 = pts[i + 1], p3 = pts[i + 2] || p2;
    const c1 = [p1[0] + (p2[0] - p0[0]) / 6, p1[1] + (p2[1] - p0[1]) / 6];
    const c2 = [p2[0] - (p3[0] - p1[0]) / 6, p2[1] - (p3[1] - p1[1]) / 6];
    d += 'C' + P(c1[0]) + ',' + P(c1[1]) + ' ' + P(c2[0]) + ',' + P(c2[1]) + ' ' + P(p2[0]) + ',' + P(p2[1]);
  }
  return d;
}
function arc(cx, cy, r0, r1, a0, a1) {
  const large = a1 - a0 > Math.PI ? 1 : 0;
  const pt = (r, a) => P(cx + r * Math.sin(a)) + ',' + P(cy - r * Math.cos(a));
  if (r0 <= 0) return 'M' + P(cx) + ',' + P(cy) + 'L' + pt(r1, a0) + 'A' + r1 + ',' + r1 + ' 0 ' + large + ' 1 ' + pt(r1, a1) + 'Z';
  return 'M' + pt(r1, a0) + 'A' + r1 + ',' + r1 + ' 0 ' + large + ' 1 ' + pt(r1, a1) +
    'L' + pt(r0, a1) + 'A' + r0 + ',' + r0 + ' 0 ' + large + ' 0 ' + pt(r0, a0) + 'Z';
}
const polar = (cx, cy, r, a) => [cx + r * Math.sin(a), cy - r * Math.cos(a)];
const tipAttr = html => ' data-tip="' + esc(html) + '"';

/* Оси: горизонтальная сетка с подписями слева */
function yGrid(x0, x1, y, ticks, f = v => fmt(v)) {
  return ticks.map(t => `<line x1="${x0}" x2="${x1}" y1="${P(y(t))}" y2="${P(y(t))}" stroke="var(--grid)" stroke-width="1"/>` +
    `<text x="${x0 - 6}" y="${P(y(t)) + 4}" text-anchor="end" font-size="11" class="muted">${f(t)}</text>`).join('');
}
function xTicks(y0, x, ticks, f = v => fmt(v), grid = 0) {
  return ticks.map(t => (grid ? `<line x1="${P(x(t))}" x2="${P(x(t))}" y1="${grid}" y2="${y0}" stroke="var(--grid)"/>` : '') +
    `<text x="${P(x(t))}" y="${y0 + 15}" text-anchor="middle" font-size="11" class="muted">${f(t)}</text>`).join('');
}

/* ---------- Подсказка ---------- */
const tip = document.createElement('div');
tip.id = 'tip';
document.addEventListener('DOMContentLoaded', () => document.body.appendChild(tip));
document.addEventListener('mousemove', e => {
  const t = e.target.closest && e.target.closest('[data-tip]');
  if (!t) { tip.style.display = 'none'; return; }
  tip.innerHTML = t.getAttribute('data-tip');
  tip.style.display = 'block';
  const w = tip.offsetWidth, h = tip.offsetHeight;
  let x = e.clientX + 14, y = e.clientY + 14;
  if (x + w > innerWidth - 8) x = e.clientX - w - 14;
  if (y + h > innerHeight - 8) y = e.clientY - h - 14;
  tip.style.left = x + 'px'; tip.style.top = y + 'px';
});

/* ---------- Карточка диаграммы ---------- */
/*
 * def: {num, title (вывод), form (вид диаграммы), goal, problem, source, render(el) -> {side, table}}
 * side - html правой колонки (закономерность, цифры); table - html таблицы "Все данные".
 */
function renderCards(host, defs) {
  host.innerHTML = '';
  for (const def of defs) {
    const card = document.createElement('section');
    card.className = 'card';
    card.id = 'c' + def.num.replace('.', '');
    card.innerHTML = `<div class="num">${esc(def.num)}</div><h2>${esc(def.title)}</h2>
      <div class="form">${esc(def.form)}</div>
      <div class="body${def.wide ? ' wide' : ''}"><div class="plot"></div><div class="side"></div></div>
      <div class="tview" hidden></div>
      <div class="cardfoot"><span class="src">Источник: ${esc(def.source)}</span><button class="tbtn">Все данные</button></div>`;
    host.appendChild(card);
    const plot = card.querySelector('.plot'), side = card.querySelector('.side'), tv = card.querySelector('.tview');
    const draw = () => {
      const res = def.render(plot) || {};
      side.innerHTML = `<div><h3>Закономерность</h3><p class="find">${res.find || ''}</p></div>` + (res.side || '') +
        `<div><h3>Цель</h3><p>${esc(def.goal)}</p></div><div><h3>Решает</h3><p>${esc(def.problem)}</p></div>`;
      tv.innerHTML = res.table || '';
    };
    def.redraw = draw;
    draw();
    card.querySelector('.tbtn').addEventListener('click', e => {
      tv.hidden = !tv.hidden; e.target.textContent = tv.hidden ? 'Все данные' : 'Скрыть данные';
    });
  }
}
function table(head, rows, numCols = []) {
  return '<table><thead><tr>' + head.map((h, i) => `<th${numCols.includes(i) ? ' class="n"' : ''}>${esc(h)}</th>`).join('') +
    '</tr></thead><tbody>' + rows.map(r => '<tr>' + r.map((c, i) => `<td${numCols.includes(i) ? ' class="n"' : ''}>${c}</td>`).join('') + '</tr>').join('') +
    '</tbody></table>';
}
function legend(items) {
  return '<div class="legend">' + items.map(it => it.ramp
    ? `<span>${esc(it.from || '')}<span class="ramp">${it.ramp.map(c => `<i style="background:${c}"></i>`).join('')}</span>${esc(it.to || '')}</span>`
    : `<span><i class="${it.kind || ''}" style="background:${it.color}"></i>${esc(it.name)}</span>`).join('') + '</div>';
}
/* Кнопки-переключатели над диаграммой: opts [{v, name}], cur, onChange(v) */
function toggles(el, opts, cur, onChange) {
  const box = document.createElement('div');
  box.className = 'ctl';
  box.innerHTML = opts.map(o => `<button data-v="${esc(o.v)}" class="${o.v === cur ? 'on' : ''}">${esc(o.name)}</button>`).join('');
  box.addEventListener('click', e => { const b = e.target.closest('button'); if (b) onChange(b.dataset.v); });
  el.prepend(box);
}

/* ---------- Тема и навигация ---------- */
function setupPage(active) {
  const nav = document.querySelector('nav.dash');
  if (nav) {
    const pages = [['index.html', 'Комплект'], ['d1.html', '1. Ритмы'], ['d2.html', '2. Сеть'], ['d3.html', '3. Вагон'],
      ['d4.html', '4. Люди'], ['d5.html', '5. Контроль'], ['d6.html', '6. Дозор']];
    nav.innerHTML = pages.map(([h, n]) => `<a href="${h}" class="${h === active ? 'on' : ''}">${n}</a>`).join('');
  }
  const th = document.getElementById('fTheme');
  if (th) th.addEventListener('change', () => {
    if (th.value) document.documentElement.setAttribute('data-theme', th.value);
    else document.documentElement.removeAttribute('data-theme');
  });
}
