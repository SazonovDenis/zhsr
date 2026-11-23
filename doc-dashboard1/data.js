/*
 * Демонстрационные данные doc-dashboard1: имитация года работы по реальным маршрутам и станциям (geo.js).
 * Генератор детерминирован: одинаковые данные при каждом открытии.
 * Структура записей повторяет таблицы БД (в комментариях - таблица-источник), без лишних полей.
 * В данные заложены закономерности, которые диаграммы должны найти; список - в readme.md, раздел "Заложенные закономерности".
 */
'use strict';

const D = (function () {
  const R = makeRnd(20261122);
  // Отдельный генератор для данных дашборда 6: не сдвигает последовательность основного генератора
  const R2 = makeRnd(777);
  const START = TODAY - 365 * DAY;

  /* ---------- Справочники ---------- */
  const DEPOTS = [
    { id: 1, name: 'Астана', st: 129 }, { id: 2, name: 'Алматы', st: 103 }, { id: 4, name: 'Караганды', st: 382 },
    { id: 5, name: 'Шымкент', st: 844 }, { id: 8, name: 'Нурлы Жол', st: 561 }
  ];
  const DEP = Object.fromEntries(DEPOTS.map(d => [d.id, d]));
  const depotByStation = Object.fromEntries(DEPOTS.map(d => [d.st, d.id]));

  const TYPES = {
    STAFF: 'Штабной', REST: 'Вагон-ресторан', SV: 'СВ', LUX: 'Люкс', K: 'Купейный', P: 'Плацкартный', S: 'Сидячий'
  };
  const MODELS = {
    1: 'Тулпар-Тальго', 2: 'Тулпар (ТВЗ/ТМХ)', 3: 'Stadler, спальный', 4: 'Stadler, кушетный', 5: 'Stadler, штабной', 0: 'Без модели'
  };
  const SERVICES = { T: 'Техническая', C: 'Клининг', E: 'Экипировка' };
  const SEV = ['', 'Низкая', 'Средняя', 'Высокая', 'Критическая'];
  const STATUS = {
    NEW: 'Новая', ASSIGNED: 'Назначена', IN_PROGRESS: 'В работе', DONE: 'Выполнена', CLOSED: 'Закрыта',
    REJECTED: 'Отклонена', DEFERRED: 'Отложена до ТО', CANCELLED: 'Отменена'
  };

  /*
   * Неисправности (fault_type, fault_group_type): w - доля в потоке, season - зима/лето, dust - пыльные участки,
   * night - проявляется ночью, board - замечают при посадке.
   */
  const FG = [];
  const FAULTS = [];
  function grp(id, name, svc, list) {
    FG.push({ id, name, svc });
    for (const f of list) FAULTS.push(Object.assign({ id: FAULTS.length + 1, group: id, svc }, f));
  }
  grp(2, 'Кузов', 'T', [{ name: 'Окно дует', sev: 2, w: 5, dust: 1, season: 'winter' }, { name: 'Трещина стекла окна', sev: 2, w: 2, dust: 1 }, { name: 'Не закрывается окно', sev: 2, w: 2 }]);
  grp(3, 'Внутренняя отделка', 'T', [{ name: 'Вздулся линолеум', sev: 1, w: 2 }, { name: 'Сломана полка', sev: 1, w: 2 }, { name: 'Порвана обивка дивана', sev: 1, w: 2 }]);
  grp(4, 'Оборудование салона', 'T', [{ name: 'Не работает кипятильник', sev: 2, w: 5, board: 1 }, { name: 'Не работает розетка 220 В', sev: 2, w: 4 }, { name: 'Течёт умывальник', sev: 2, w: 3 }]);
  grp(5, 'Ходовая часть', 'T', [{ name: 'Стук колёсной пары', sev: 3, w: 1.5 }, { name: 'Перегрев буксы', sev: 4, w: 0.8, dust: 1 }, { name: 'Вибрация тележки', sev: 3, w: 1.2 }]);
  grp(6, 'Энергоснабжение', 'T', [{ name: 'Нет напряжения в вагоне', sev: 3, w: 2, night: 1 }, { name: 'Неисправен подвагонный генератор', sev: 3, w: 1.5 }]);
  grp(8, 'Вспомогательные системы', 'T', [{ name: 'Не работают биотуалеты', sev: 3, w: 7 }, { name: 'Не работает водонагреватель', sev: 2, w: 4, board: 1 }, { name: 'Течь воды в туалете из-под потолка', sev: 4, w: 1.5 }]);
  grp(10, 'Освещение', 'T', [{ name: 'Не горит свет в купе', sev: 2, w: 4, night: 1 }, { name: 'Не работает ночное освещение', sev: 2, w: 3, night: 1 }]);
  grp(11, 'Отопление и кондиционирование', 'T', [{ name: 'Не греет отопление', sev: 3, w: 6, season: 'winter', night: 1 }, { name: 'Холодно в вагоне', sev: 3, w: 4, season: 'winter', night: 1 },
    { name: 'Не работает кондиционер', sev: 3, w: 5, season: 'summer' }, { name: 'Кондиционер не охлаждает', sev: 3, w: 4, season: 'summer', dust: 1 }]);
  grp(13, 'Двери и входы', 'T', [{ name: 'Не закрывается дверь купе', sev: 2, w: 3 }, { name: 'Не работает замок в туалете', sev: 2, w: 4 }]);
  grp(16, 'Тормоза', 'T', [{ name: 'Износ тормозных колодок', sev: 3, w: 1 }, { name: 'Не отпускает тормоз', sev: 4, w: 0.5 }]);
  grp(17, 'Сцепка и переходы', 'T', [{ name: 'Сквозит в межвагонном переходе', sev: 3, w: 2.5, season: 'winter' }, { name: 'Повреждено суфле перехода', sev: 3, w: 1, dust: 1 }]);
  grp(20, 'Мягкий съёмный инвентарь', 'E', [{ name: 'Не хватает подушек', sev: 2, w: 2, board: 1 }, { name: 'Не хватает одеял', sev: 2, w: 2.5, board: 1, season: 'winter' }, { name: 'Порван матрац', sev: 2, w: 1.5, board: 1 }]);
  grp(23, 'Посуда', 'E', [{ name: 'Не хватает стаканов', sev: 1, w: 2, board: 1 }, { name: 'Не хватает подстаканников', sev: 1, w: 1.5, board: 1 }]);
  grp(25, 'Медицинские принадлежности', 'E', [{ name: 'Не укомплектована аптечка', sev: 3, w: 1, board: 1 }]);
  grp(27, 'Дополнительные сервисные услуги', 'E', [{ name: 'Нет питьевой воды в бутылках', sev: 1, w: 2, season: 'summer' }, { name: 'Не работает Wi-Fi', sev: 1, w: 3 }]);
  grp(28, 'Уборка вагона', 'C', [{ name: 'Грязно в туалете', sev: 1, w: 8 }, { name: 'Не вынесен мусор', sev: 1, w: 5 }, { name: 'Грязные окна', sev: 1, w: 3, dust: 1 }, { name: 'Пыль на полках', sev: 1, w: 3, dust: 1, board: 1 }]);
  grp(29, 'Расходные материалы (ТМЦ)', 'C', [{ name: 'Нет туалетной бумаги', sev: 1, w: 5 }, { name: 'Нет жидкого мыла', sev: 1, w: 3 }]);
  grp(99, 'Экипировка по норме', 'E', [{ name: 'Недостача экипировки по норме', sev: 2, w: 0, auto: 1 }]);
  const FBY = Object.fromEntries(FAULTS.map(f => [f.name, f]));
  const FGBY = Object.fromEntries(FG.map(g => [g.id, g]));

  /* Сезонность: доля зимы и лета по дню года; холодная волна 12-22 января */
  const COLD = [Date.UTC(2026, 0, 12), Date.UTC(2026, 0, 23)];
  function season(t) {
    const doy = (t - Date.UTC(new Date(t).getUTCFullYear(), 0, 1)) / DAY;
    const winter = Math.max(0, Math.cos((doy - 20) / 365 * 2 * Math.PI));
    const summer = Math.max(0, -Math.cos((doy - 20) / 365 * 2 * Math.PI));
    return { winter: winter * (t >= COLD[0] && t < COLD[1] ? 2.6 : 1), summer };
  }
  const HOLIDAYS = [[Date.UTC(2025, 11, 30), Date.UTC(2026, 0, 3)], [Date.UTC(2026, 2, 20), Date.UTC(2026, 2, 24)], [Date.UTC(2026, 4, 1), Date.UTC(2026, 4, 10)]];
  const holiday = t => HOLIDAYS.some(([a, b]) => t >= a && t < b);

  /* ---------- Маршруты ---------- */
  const ROUTE_CFG = {
    1: { home: 1, freq: 1, kind: 'long', h: [19, 21, 21] }, 103: { home: 4, freq: 1, kind: 'long', h: [18, 22, 22] },
    3: { home: 1, freq: 2, kind: 'med', loop: 1, h: [16, 18, 18] }, 101: { home: 8, freq: 2, kind: 'local', loop: 1, h: [17, 22, 22] },
    102: { home: 8, freq: 1, kind: 'local', loop: 1, h: [9, 9, 9] }, 201: { home: 2, freq: 1, kind: 'long' },
    202: { home: 1, freq: 1, kind: 'med' }, 203: { home: 2, freq: 1, kind: 'long' }, 204: { home: 5, freq: 1, kind: 'med' },
    205: { home: 1, freq: 2, kind: 'med' }
  };
  const stName = sid => GEO.stations[sid][0];
  const ROUTES = GEO.routes.map(g => {
    const c = ROUTE_CFG[g.id], st = g.st, n = st.length, dep0 = st[0][4];
    const T = st.map((s, i) => i === 0 ? 0 : ((s[3] != null ? s[3] : s[4]) - dep0));
    const K = st.map(s => s[1]);
    const durMin = T[n - 1];
    const r = {
      id: g.id, num: g.num, name: g.name, st, n, T, K, total: K[n - 1], dep0, durMin, home: c.home, freq: c.freq,
      kind: c.kind, loop: !!c.loop, hours: c.h || [Math.round(durMin / 60), Math.round(durMin / 60) + 2, Math.round(durMin / 60) + 2],
      stops: st.filter(s => s[2]).map(s => ({ sid: s[0], km: s[1], name: stName(s[0]) }))
    };
    r.short = r.num + ' ' + r.name;
    return r;
  });
  const RBY = Object.fromEntries(ROUTES.map(r => [r.id, r]));
  function interp(X, Y, x) {
    if (x <= X[0]) return Y[0];
    for (let i = 1; i < X.length; i++) if (x <= X[i]) return Y[i - 1] + (Y[i] - Y[i - 1]) * (x - X[i - 1]) / ((X[i] - X[i - 1]) || 1);
    return Y[Y.length - 1];
  }
  /* Физический километр маршрута по минутам от отправления (по расписанию route_station) */
  const kmAt = (r, dir, m) => dir ? interp(r.T, r.K, r.durMin - m) : interp(r.T, r.K, m);
  const minAt = (r, dir, km) => dir ? r.durMin - interp(r.K, r.T, km) : interp(r.K, r.T, km);
  function kmOfStation(r, name) { const s = r.st.find(x => stName(x[0]) === name); return s ? s[1] : null; }
  function section(r, a, b) { const x = kmOfStation(r, a), y = kmOfStation(r, b); return x == null || y == null ? null : [Math.min(x, y), Math.max(x, y)]; }
  const DUST = [['Сары-Шаган', 'Шу'], ['Актогай', 'Аягоз'], ['Туркестан', 'Кызылорда'], ['Кашкантениз', 'Берлик 2']];
  const DEAD = [['Мойынты', 'Сары-Шаган'], ['Мойынты', 'Балхаш 1'], ['Есиль-1', 'Аркалык'], ['Аягоз', 'Семей']];
  for (const r of ROUTES) {
    r.dust = DUST.map(([a, b]) => section(r, a, b)).filter(Boolean).slice(0, 1);
    r.dead = DEAD.map(([a, b]) => section(r, a, b)).filter(Boolean).slice(0, 1);
  }
  const inSec = (secs, km) => secs.some(([a, b]) => km >= a && km <= b);

  /* ---------- Вагоны (wagon) ---------- */
  const COMPO = {
    long: ['STAFF', 'REST', 'LUX', 'SV', 'SV', 'K', 'K', 'K', 'K', 'K', 'P', 'P', 'P', 'P'],
    med: ['STAFF', 'K', 'K', 'K', 'K', 'P', 'P', 'P', 'P', 'P'],
    local: ['STAFF', 'P', 'P', 'P', 'P', 'S', 'S', 'S']
  };
  const WAGONS = [];
  let wagonNo = 0;
  function makeWagon(type, depot, long) {
    let model = 0, maker = 'ТВЗ', build;
    const x = R();
    if (type === 'K') { if (x < 0.55) { model = 1; maker = 'Тулпар-Тальго'; build = R.int(2009, 2016); } else build = R.int(1986, 2007); }
    else if (type === 'SV' || type === 'LUX') { if (x < 0.5 && type === 'SV') { model = 3; maker = 'Stadler'; build = R.int(2025, 2026); } else { model = 1; maker = 'Тулпар-Тальго'; build = R.int(2010, 2016); } }
    else if (type === 'P') {
      if (long && x < 0.22) { model = 4; maker = 'Stadler'; build = R.int(2025, 2026); }
      else if (x < 0.6) { model = 2; maker = 'ТМХ'; build = R.int(2019, 2023); } else build = R.int(1985, 2005);
    } else if (type === 'STAFF') { if (x < 0.4) { model = 5; maker = 'Stadler'; build = R.int(2024, 2026); } else build = R.int(1990, 2008); }
    else if (type === 'REST') { maker = 'Аммендорф'; build = R.int(1987, 1998); }
    else build = R.int(1984, 1999);
    const inService = model >= 3 && build >= 2025 ? Date.UTC(build, R.int(0, build === 2026 ? 6 : 11), R.int(1, 28)) : Date.UTC(build, 5, 1);
    const w = {
      id: ++wagonNo, number: String(10000000 + Math.floor(R() * 89999999)).slice(0, 8), type, typeName: TYPES[type],
      model, modelName: MODELS[model], maker, build, depot, inService, run0: model >= 3 ? 0 : R.int(200, 3000) * 1000,
      busyUntil: 0, repairUntil: 0, nextTO: START + R.between(0, 35) * DAY, nextTR: START + R.between(20, 180) * DAY,
      trips: [], repairs: [], spare: false, km: 0
    };
    WAGONS.push(w);
    return w;
  }
  const TRAINS = [];
  for (const r of ROUTES) {
    const cycleH = r.loop ? r.durMin / 60 + 10 : 2 * r.durMin / 60 + 16;
    const pool = Math.max(1, Math.ceil(cycleH / (24 * r.freq)));
    r.trains = [];
    for (let i = 0; i < pool; i++) {
      const tr = { id: TRAINS.length + 1, name: 'П-' + r.num.split('/')[0] + '-' + (i + 1), route: r.id, base: [], cur: [] };
      tr.base = COMPO[r.kind].map(t => makeWagon(t, r.home, r.kind === 'long'));
      tr.cur = tr.base.slice();
      TRAINS.push(tr); r.trains.push(tr);
    }
  }
  for (const d of DEPOTS) {
    const base = WAGONS.filter(w => w.depot === d.id).length;
    const n = d.id === 4 ? 14 : Math.round(base * 0.2) + 3;
    for (let i = 0; i < n; i++) {
      const w = makeWagon(R.pick(['K', 'P', 'P', 'SV', 'S', 'STAFF'], [30, 35, 10, 8, 12, 5]), d.id, true);
      w.spare = true;
    }
  }
  const WBY = Object.fromEntries(WAGONS.map(w => [w.id, w]));
  // Исключённые вагоны (OUT_OF_SERVICE) и вагоны, забытые в парке отстоя
  const spares = WAGONS.filter(w => w.spare).sort((a, b) => a.build - b.build);
  spares.slice(0, 6).forEach((w, i) => { w.excludedFrom = TODAY - (200 - i * 30) * DAY; });
  spares.filter(w => (w.depot === 1 || w.depot === 8) && !w.excludedFrom).slice(0, 9)
    .forEach((w, i) => { w.forgottenFrom = TODAY - (128 - i * 3) * DAY; });

  /* ---------- Сотрудники (usr, usr_role, usr_depot, usr_category) ---------- */
  const SURN = ['Абенов', 'Ахметов', 'Байжанов', 'Бекова', 'Ворошилов', 'Галиева', 'Дюсенов', 'Ержанова', 'Жумабаев', 'Закирова',
    'Иванова', 'Исаев', 'Калиева', 'Ким', 'Кузнецов', 'Мусин', 'Нурланов', 'Оспанова', 'Петров', 'Рахимов', 'Сидорова', 'Смагулов',
    'Тлеубаев', 'Уразов', 'Федорова', 'Хасенов', 'Цой', 'Шаяхметов', 'Ермеков', 'Юсупова', 'Ли', 'Есенова', 'Попов', 'Сейткали',
    'Тажибаева', 'Омаров', 'Амирова', 'Бектуров', 'Гусева', 'Даулетов', 'Жанибекова', 'Искаков', 'Козлова', 'Мамырова', 'Нуркенов'];
  const INI = 'АБГДЕЖЗИКЛМНОРСТУЭ';
  const USERS = [];
  const usedNames = new Set();
  function makeUser(role, depot, extra = {}) {
    let name;
    do { name = R.pick(SURN) + ' ' + R.pick(INI) + '.' + R.pick(INI) + '.'; } while (usedNames.has(name));
    usedNames.add(name);
    const u = Object.assign({ id: USERS.length + 1, name, role, depot, busyUntil: 0, hours: 0, pref: 1 }, extra);
    USERS.push(u);
    return u;
  }
  const CREW = {};
  for (const d of DEPOTS) {
    const rs = ROUTES.filter(r => r.home === d.id);
    if (!rs.length) { CREW[d.id] = { cond: [], lnp: [], pem: [] }; continue; }
    const seatsPerDay = sum(rs.map(r => COMPO[r.kind].length * (r.loop ? 1 : 2) / r.freq));
    const lnpPerDay = sum(rs.map(r => (r.loop ? 1 : 2) / r.freq));
    const hoursPerTrip = mean(rs.map(r => r.durMin / 60 + 24));
    const cond = [], lnp = [], pem = [];
    const nCond = Math.round(seatsPerDay * (hoursPerTrip + 44) / 24 * 0.85);
    for (let i = 0; i < nCond; i++) {
      cond.push(makeUser('CONDUCTOR', d.id, {
        cat: R.pick(['TRAINEE', 'GRADE_2', 'GRADE_3', 'SENIOR'], [12, 30, 43, 15]),
        vigil: clamp(R.normal(0.74, 0.1), 0.45, 0.95), pref: R.chance(0.12) ? 3 : 1
      }));
    }
    const nL = Math.round(lnpPerDay * (hoursPerTrip + 44) / 24 * 1.25) + 1;
    for (let i = 0; i < nL; i++) lnp.push(makeUser('LNP', d.id, { dilig: clamp(R.normal(0.62, 0.2), 0.2, 0.95) }));
    for (let i = 0; i < nL; i++) pem.push(makeUser('PEM', d.id, {}));
    CREW[d.id] = { cond, lnp, pem };
  }
  // Проводники, которые редко пишут заявки сами (заложенная закономерность "эстафета ответственности")
  [CREW[1].cond[3], CREW[1].cond[17], CREW[2].cond[5]].forEach(u => { if (u) u.vigil = 0.22; });
  // Постоянные бригады у ЛНП депо Нурлы Жол
  // Бригада из восьми проводников закреплена за ЛНП; несколько ЛНП делят одну бригаду по очереди
  {
    const cr = CREW[8], nTeams = Math.max(1, Math.floor(cr.cond.length / 7));
    cr.lnp = cr.lnp.slice(0, nTeams); cr.pem = cr.pem.slice(0, nTeams);
    cr.lnp.forEach((l, i) => { l.team = cr.cond.slice(i * 7, i * 7 + 7); l.team.forEach(c => { c.teamOf = l.id; }); });
  }
  const RECEIVERS = {}, EXEC = {};
  for (const d of DEPOTS) {
    RECEIVERS[d.id] = [makeUser('RECEIVER', d.id), makeUser('MASTER', d.id), makeUser('RECEIVER', d.id)];
    EXEC[d.id] = {
      T: [0, 1, 2, 3].map(() => makeUser('MASTER', d.id, { service: 'T' })),
      C: [0, 1].map(() => makeUser('CLEANING', d.id, { service: 'C' })),
      E: [0, 1].map(() => makeUser('EQUIPPER', d.id, { service: 'E' }))
    };
  }
  EXEC[2].T[1].formal = true; EXEC[4].T[0].formal = true; EXEC[1].C[1].formal = true;
  const UBY = Object.fromEntries(USERS.map(u => [u.id, u]));
  // Привычки авторов заявок: завышение критичности (inflate) и доля необоснованных заявок (rejK)
  USERS.forEach(u => {
    u.inflate = u.role === 'CONDUCTOR' ? (R2.chance(0.1) ? 0.38 : 0.04) : u.role === 'LNP' ? 0.06 : 0.02;
    u.rejK = u.role === 'CONDUCTOR' && R2.chance(0.08) ? 3.5 : 1;
  });
  function inflate(f, uid) {
    const u = UBY[uid];
    if (!u || !R2.chance(u.inflate)) return f.sev;
    return Math.min(4, f.sev + (f.sev <= 2 && R2.chance(0.4) ? 2 : 1));
  }
  // План и факт прицепок и отцепок (train_wagon_change): доля планирования по депо приписки поезда
  const COUPLINGS = [];
  const PLAN_SHARE = { 1: 0.78, 2: 0.42, 4: 0.7, 5: 0.6, 8: 0.86 };

  /* ---------- Рейсы (trip), состав (train_wagon), бригада (trip_crew), ремонты (wagon_status, transfer_act) ---------- */
  const TRIPS = [];
  const plans = [];
  for (const r of ROUTES) {
    let k = 0;
    for (let day = START; day < TODAY + DAY; day += DAY * r.freq, k++) {
      const plan = day + r.dep0 * MIN;
      const train = r.trains[k % r.trains.length];
      plans.push({ r, dir: 0, plan, train });
      if (!r.loop) {
        const back = Math.ceil((plan + r.durMin * MIN + 8 * HOUR) / (10 * MIN)) * 10 * MIN;
        plans.push({ r, dir: 1, plan: back, train });
      }
    }
  }
  plans.sort((a, b) => a.plan - b.plan);

  const REPAIRS = [];
  const slowK = (depot, t) => depot === 4 ? (t > TODAY - 45 * DAY ? 2.3 : 1.35) : 1;
  function addRepair(w, start, work, depot) {
    const base = { TO: [0.5, 1.6], TR1: [3, 7], TOR: [1, 4] }[work];
    const d = R.between(base[0], base[1]) * slowK(depot, start) * DAY;
    const rep = { wagon: w.id, work, depot, from: start, to: start + d };
    REPAIRS.push(rep); w.repairs.push(rep);
    w.repairUntil = rep.to;
    if (work === 'TO') w.nextTO = rep.to + R.between(25, 40) * DAY;
    if (work === 'TR1') { w.nextTR = rep.to + R.between(140, 220) * DAY; w.nextTO = rep.to + R.between(25, 40) * DAY; }
  }
  function trDepot(w) {
    if (w.depot === 1) return R.chance(0.4) ? 4 : 1;
    if (w.depot === 8) return R.chance(0.7) ? 1 : 8;
    if (w.depot === 5) return R.chance(0.5) ? 2 : 5;
    return w.depot;
  }
  const free = (w, t) => w.busyUntil <= t && w.repairUntil <= t && !(w.forgottenFrom && t >= w.forgottenFrom) && !(w.excludedFrom && t >= w.excludedFrom);
  function maintain(w, t) {
    if (w.busyUntil > t || w.repairUntil > t) return;
    if (t >= w.nextTR) { addRepair(w, Math.max(w.busyUntil + 2 * HOUR, w.nextTR), 'TR1', trDepot(w)); return; }
    if (t >= w.nextTO) addRepair(w, Math.max(w.busyUntil + 2 * HOUR, w.nextTO), 'TO', w.depot);
  }
  function pickSpare(depot, type, t) {
    const pool = WAGONS.filter(w => w.spare && free(w, t));
    return pool.find(w => w.depot === depot && w.type === type) || pool.find(w => w.depot === depot) ||
      pool.find(w => w.type === type) || null;
  }
  function pickCrew(list, t, n, team) {
    const out = [];
    // Член чужой постоянной бригады в рейс с другим ЛНП не ставится, пока есть свободные
    const own = u => !u.teamOf || (team && team.includes(u));
    let avail = list.filter(u => u.busyUntil <= t && own(u));
    if (avail.length < n) avail = list.filter(u => u.busyUntil <= t);
    if (team) for (const u of team) if (out.length < n && u.busyUntil <= t) out.push(u);
    while (out.length < n && avail.length) {
      const cand = avail.filter(u => !out.includes(u));
      if (!cand.length) break;
      const minH = Math.min(...cand.map(u => u.hours / u.pref));
      const best = cand.filter(u => u.hours / u.pref <= minH + 30);
      out.push(R.pick(best));
    }
    return out;
  }

  const REQS = [];
  const CHECKS = [];
  const EQUIPS = [];
  let reqNo = 0;
  /* Пункты чек-листа «Приёмка вагона»: у норматива пункта одна неисправность заявки или ни одной (check_list_item_norm.fault_type) */
  const CHECK_ITEMS = [
    { name: 'Пожарная сигнализация', p: 0.012, fault: 'Нет напряжения в вагоне' },
    { name: 'Сигнализация нагрева букс', p: 0.01, fault: 'Перегрев буксы' },
    { name: 'Температура в вагоне, 20-24 °C', p: null, fault: null },
    { name: 'Есть горячая вода', p: 0.07, fault: 'Не работает водонагреватель' },
    { name: 'Санитарный узел исправен', p: 0.012, fault: 'Не работают биотуалеты' },
    { name: 'Общее освещение', p: 0.06, fault: 'Не горит свет в купе' },
    { name: 'Окна и уплотнители', p: 0.05, fault: 'Окно дует' },
    { name: 'Двери купе и тамбура', p: 0.035, fault: 'Не закрывается дверь купе' },
    { name: 'Розетки в купе', p: 0.02, fault: 'Не работает розетка 220 В' },
    { name: 'Чистота вагона', p: 0.09, fault: 'Грязно в туалете' }
  ];
  const EQUIP_ITEMS = [
    { name: 'Полотенце махровое', p: 0.05 }, { name: 'Стакан с подстаканником', p: 0.03 }, { name: 'Ложка чайная', p: 0.06 },
    { name: 'Одеяло', p: 0.012 }, { name: 'Подушка', p: 0.008 }, { name: 'Наволочка', p: 0.025 }, { name: 'Простыня', p: 0.02 },
    { name: 'Чайник для заварки', p: 0.015 }, { name: 'Огнетушитель', p: 0.003 }, { name: 'Аптечка текущая', p: 0.02 },
    { name: 'Флажки', p: 0.008 }, { name: 'Термометр комнатный', p: 0.03 }
  ];
  const EQUIP_ROUTE = {
    103: { 0: 3.2, 1: 5.5 }, 1: { 0: 2.4, 1: 1.6 }, 201: { 0: 2.8, 2: 2 }, 204: { 2: 3.5, 7: 2.5 }, 203: { 3: 3, 4: 2 }, 102: { 11: 4 }
  };

  function faultWeights(t, r, w) {
    const s = season(t);
    return FAULTS.map(f => {
      let x = f.w;
      if (f.season === 'winter') x *= 0.1 + 2.4 * s.winter;
      if (f.season === 'summer') x *= 0.1 + 2.2 * s.summer;
      if (f.dust && r.dust.length) x *= 2.2;
      if (f.svc === 'C' && holiday(t)) x *= 6;
      if (f.svc === 'E' && holiday(t)) x *= 2;
      if (w.type === 'REST' && f.group === 4) x *= 2;
      return x;
    });
  }
  const BASE_W = sum(FAULTS.map(f => f.w)) * 0.85;
  function newReq(o) {
    const q = Object.assign({ id: ++reqNo, trip: null, km: null, sts: [], executor: null, returns: 0 }, o);
    q.number = 'KZR-' + String(new Date(q.created).getUTCFullYear()).slice(2) + '-' + String(q.id).padStart(5, '0');
    q.svc = q.fault.svc;
    q.sevDefault = q.fault.sev;
    REQS.push(q);
    return q;
  }

  for (const p of plans) {
    const r = p.r, train = p.train;
    if (p.plan > NOW + 2 * DAY) continue;
    const cancelled = p.plan < NOW && R.chance(0.012);
    const delayDep = R.chance(0.82) ? R.exp(4) : R.exp(28);
    const late = r.id === 203 ? R.logn(40, 0.8) : r.kind === 'long' ? R.logn(14, 0.9) : R.logn(7, 0.9);
    const coldLate = p.plan >= COLD[0] && p.plan < COLD[1] ? R.between(30, 120) : 0;
    const trip = {
      id: TRIPS.length + 1, route: r.id, dir: p.dir, train: train.id, plan: p.plan,
      dep: p.plan + delayDep * MIN, arr: p.plan + (r.durMin + delayDep + late + coldLate - 6) * MIN,
      wagons: [], cond: [], lnp: null, pem: null, status: 'PLANNED'
    };
    trip.status = cancelled ? 'CANCELLED' : trip.plan > NOW ? 'PLANNED' : trip.arr > NOW ? 'IN_PROGRESS' : 'DONE';
    const endSt = p.dir || r.loop ? r.st[0][0] : r.st[r.n - 1][0];
    trip.arrDepot = depotByStation[endSt] || r.home;
    TRIPS.push(trip);
    if (cancelled || trip.status === 'PLANNED') continue;

    // Состав: вагоны базового состава поезда; вагон в ремонте заменяется резервным
    const t0 = trip.dep;
    train.cur = train.base.map((bw, i) => {
      maintain(bw, t0);
      if (free(bw, t0)) return bw;
      const c = train.cur[i];
      if (c && c !== bw && free(c, t0)) return c;
      return pickSpare(r.home, bw.type, t0);
    });
    trip.wagons = train.cur.filter(Boolean).map(w => w.id);
    if (train.lastIds) {
      const changed = trip.wagons.filter(id => !train.lastIds.includes(id)).length;
      for (let k = 0; k < changed * 2; k++) {
        let kind = 'unplanned', h = 0;
        if (R2.chance(PLAN_SHARE[r.home])) {
          h = R2.chance(0.72) ? R2.normal(0, 0.4) : R2.logn(4, 0.8);
          kind = Math.abs(h) <= 1 ? 'ontime' : 'late';
        }
        COUPLINGS.push({ t: t0, train: train.id, depot: r.home, kind, h });
      }
      if (R2.chance(r.home === 2 ? 0.16 : 0.06)) COUPLINGS.push({ t: t0, train: train.id, depot: r.home, kind: 'cancelled', h: 0 });
    }
    train.lastIds = trip.wagons.slice();
    // Бригада
    const home = CREW[r.home];
    const lnp = pickCrew(home.lnp, t0, 1)[0] || home.lnp[0];
    const pem = pickCrew(home.pem, t0, 1)[0] || home.pem[0];
    const nCond = trip.wagons.filter(id => WBY[id].type !== 'REST').length;
    const cond = pickCrew(home.cond, t0, nCond, lnp.team);
    trip.lnp = lnp.id; trip.pem = pem.id;
    const restEnd = trip.arr + 44 * HOUR;
    // «Любимчиков» нарядчик ставит на рейс без полного отдыха
    for (const u of [lnp, pem, ...cond]) u.busyUntil = u.pref > 1 ? trip.arr + 20 * HOUR : restEnd;
    lnp.hours += r.hours[1]; pem.hours += r.hours[2];
    let ci = 0;
    trip.cond = trip.wagons.map(id => WBY[id].type === 'REST' ? null : (cond[ci++] || cond[0] || null));
    trip.cond.forEach(u => { if (u) u.hours += r.hours[0]; });
    trip.cond = trip.cond.map(u => u ? u.id : null);

    const hours = (trip.arr - trip.dep) / HOUR;
    const weights = null;
    trip.wagons.forEach((wid, wi) => {
      const w = WBY[wid];
      w.busyUntil = trip.arr;
      w.trips.push(trip.id);
      w.km += r.total;
      const c = trip.cond[wi] ? UBY[trip.cond[wi]] : null;
      // Чек-лист приёмки вагона перед отправлением (check_list)
      const checked = !!c && R.chance(lnp.dilig * (r.kind === 'long' ? 1 : 0.55));
      const age = (t0 - w.inService) / (365 * DAY);
      if (checked) {
        const s = season(t0);
        let temp = s.winter > s.summer ? R.normal(22, 0.9) : R.normal(22.5, 0.9);
        if (s.winter > 0.4 && R.chance(0.06 * (w.model === 1 ? 0.5 : 1.3))) temp = R.normal(17, 1.2);
        if (s.summer > 0.4 && R.chance(0.07 * (w.model === 0 ? 1.6 : 0.7))) temp = R.normal(26.5, 1.2);
        temp = Math.round(temp * 10) / 10;
        let viol = 0;
        CHECK_ITEMS.forEach((it, k) => {
          const bad = it.p == null ? (temp < 20 || temp > 24) : R.chance(it.p * 0.5 * (w.model === 0 ? 1.5 : 1));
          if (bad) viol |= 1 << k;
        });
        const ct = t0 - R.between(35, 150) * MIN;
        const chk = { id: CHECKS.length + 1, wagon: wid, trip: trip.id, route: r.id, author: c.id, lnp: lnp.id, t: ct, temp, viol, model: w.model };
        CHECKS.push(chk);
        CHECK_ITEMS.forEach((it, k) => {
          if ((viol & (1 << k)) && it.fault) {
            const f = FBY[it.fault];
            newReq({ wagon: wid, trip: trip.id, created: ct + 2 * MIN, received: ct + 3 * MIN, fault: f, sev: f.sev, author: c.id, role: 'CONDUCTOR', src: 'check', check: chk.id, km: r.loop || !p.dir ? 0 : r.total });
          }
        });
      }
      // Неисправности в пути
      let lambda = 0.15 * hours / 20;
      lambda *= age > 30 ? 1.6 : age > 20 ? 1.35 : age > 10 ? 1.05 : 0.9;
      lambda *= w.model === 1 ? 0.8 : w.model >= 3 ? (age < 1.2 ? 2.4 : 0.65) : 1;
      if (checked) lambda *= hours > 12 ? 0.66 : 0.95;
      const fw = faultWeights(t0, r, w);
      lambda *= sum(fw) / BASE_W;
      const nF = R.poisson(lambda);
      if (!nF) return;
      for (let k = 0; k < nF; k++) {
        const f = R.pick(FAULTS, fw);
        let km;
        const rand = () => R.between(0, r.total);
        if (f.dust && r.dust.length && R.chance(0.72)) km = R.between(r.dust[0][0], r.dust[0][1]);
        else if ((f.board || f.svc !== 'T') && R.chance(0.5)) km = p.dir && !r.loop ? r.total - R.between(0, r.total * 0.12) : R.between(0, r.total * 0.12);
        else if (f.night && R.chance(0.65)) {
          km = rand();
          for (let tries = 0; tries < 12; tries++) {
            const h = hourOf(trip.dep + minAt(r, p.dir, km) * MIN);
            if (h >= 22 || h < 6) break;
            km = rand();
          }
        } else km = rand();
        const frac = minAt(r, p.dir, km) / r.durMin;
        const created = trip.dep + frac * (trip.arr - trip.dep) + R.between(2, 25) * MIN;
        let received = created + R.between(0.1, 3) * MIN;
        if (inSec(r.dead, km)) {
          const [a, b] = r.dead[0];
          const exitKm = p.dir ? a : b;
          const exitT = trip.dep + minAt(r, p.dir, exitKm) / r.durMin * (trip.arr - trip.dep);
          received = Math.max(received, exitT + R.between(1, 15) * MIN);
        } else if (R.chance(0.03)) received += R.between(10, 120) * MIN;
        // Кто заявил: свой проводник, ПЭМ, ЛНП; незамеченное находят после рейса
        let author = null, role = null;
        const v = c ? c.vigil : 0;
        if (c && R.chance(f.svc === 'T' ? v * 0.85 : v)) { author = c.id; role = 'CONDUCTOR'; }
        else if (f.svc === 'T' && R.chance(0.3)) { author = pem.id; role = 'PEM'; }
        else if (R.chance(0.15)) { author = lnp.id; role = 'LNP'; }
        if (author) {
          newReq({ wagon: wid, trip: trip.id, created, received, fault: f, sev: inflate(f, author), author, role, src: 'manual', km: Math.round(km) });
        } else if (R.chance(0.65)) {
          const rc = R.pick(RECEIVERS[trip.arrDepot]);
          const ct = trip.arr + R.between(0.5, 20) * HOUR;
          newReq({ wagon: wid, trip: null, created: ct, received: ct + MIN, fault: f, sev: f.sev, author: rc.id, role: rc.role, src: 'manual', found: trip.id });
        }
        if (f.svc === 'T' && f.sev >= 3 && R.chance(0.3)) {
          const rd = (r.id === 1 || r.id === 103) && R.chance(0.5) ? 4 : r.home;
          w.busyUntil = trip.arr;
          addRepair(w, trip.arr + R.between(1, 6) * HOUR, 'TOR', rd);
        }
      }
    });
    // Передача ответственности после прибытия (wagon_responsible): план после рейса или ожидание сотрудника станции
    if (trip.arr < NOW) {
      const wkend = wday(trip.arr) >= 5, night = hourOf(trip.arr) >= 22 || hourOf(trip.arr) < 6;
      trip.hand = trip.wagons.map(() => R2.chance(0.25 + 0.6 * lnp.dilig) ? 0
        : R2.logn(4.5, 0.7) * (wkend ? 2.4 : 1) * (night ? 1.7 : 1) * (trip.arrDepot === 5 ? 1.5 : 1));
    }
    // Опись экипировки после прибытия (equip_list)
    if (trip.arr < NOW) trip.wagons.forEach(wid => {
      if (!R.chance(0.22)) return;
      const f = EQUIP_ROUTE[r.id] || {};
      let short = 0;
      EQUIP_ITEMS.forEach((it, k) => {
        let pr = it.p * (f[k] || 1);
        if (k === 3 && season(trip.arr).winter > 0.5) pr *= 2;
        if (R.chance(pr)) short |= 1 << k;
      });
      const et = trip.arr + R.between(1, 10) * HOUR;
      const eq = { id: EQUIPS.length + 1, wagon: wid, trip: trip.id, route: r.id, t: et, short };
      EQUIPS.push(eq);
      if (short) {
        const au = EXEC[r.home] ? EXEC[r.home].E[0] : RECEIVERS[r.home][0];
        newReq({ wagon: wid, trip: trip.id, created: et, received: et + MIN, fault: FBY['Недостача экипировки по норме'], sev: 2, author: au.id, role: 'EQUIPPER', src: 'equip', equip: eq.id });
      }
    });
  }

  /* ---------- Жизнь заявки: статусы (repair_request_status), исполнители (repair_request_executor) ---------- */
  const REJ = { CONDUCTOR: 0.085, LNP: 0.04, PEM: 0.03, RECEIVER: 0.035, MASTER: 0.03, EQUIPPER: 0.01 };
  function execDepot(q) {
    if (q.trip) {
      const t = TRIPS[q.trip - 1], r = RBY[t.route];
      if ((r.id === 1 || r.id === 103) && q.svc === 'T' && R.chance(0.3)) return 4;
      return t.arrDepot;
    }
    return WBY[q.wagon].depot;
  }
  function lifecycle(q) {
    const st = [];
    let t = q.created;
    const add = (s, dt) => { t += dt; st.push({ s, t }); };
    add('NEW', 0);
    const dep = execDepot(q);
    q.depot = dep;
    const pool = EXEC[dep][q.svc];
    const ex = R.pick(pool);
    const slow = slowK(dep, q.created);
    const react = (q.svc === 'T' ? (q.sev === 4 ? R.logn(0.4, 0.6) : q.sev === 3 ? R.logn(1.6, 0.7) : R.logn(4.5, 0.8))
      : q.svc === 'C' ? R.logn(2, 0.7) : R.logn(8, 0.8)) * slow * HOUR;
    if (R.chance(0.025)) { add('CANCELLED', R.between(2, 9) * MIN); q.sts = st; return; }
    const rej = q.src === 'check' ? 0.015 : q.src === 'equip' ? 0.01 : (REJ[q.role] || 0.03) * ((UBY[q.author] || {}).rejK || 1);
    if (R.chance(rej)) { add('REJECTED', react); q.sts = st; return; }
    if (R.chance(0.5)) { add('ASSIGNED', react * 0.35); q.executor = ex.id; }
    // Мелкие заявки часто ждут днями: очередь у служб
    if (q.sev <= 2 && R.chance(q.sev === 1 ? 0.18 : 0.08)) t += R.logn(6, 0.9) * slow * slow * DAY;
    if (q.svc === 'T' && q.sev < 4 && R.chance(0.07)) {
      add('DEFERRED', react * 0.6);
      const x = R();
      if (x < 0.2) { add('CANCELLED', R.between(10, 60) * DAY); q.sts = st; return; }
      if (x < 0.45) { q.sts = st; q.lost = true; return; }
      add('IN_PROGRESS', R.between(5, 45) * DAY);
    } else add('IN_PROGRESS', react * (st.length > 1 ? 0.65 : 1));
    q.executor = ex.id;
    const execT = () => ex.formal && R.chance(0.65) ? R.between(2, 10) * MIN
      : (q.svc === 'T' ? R.logn(2.5, 0.8) : q.svc === 'C' ? R.logn(0.8, 0.7) : R.logn(3, 0.8)) * (dep === 4 ? 1.3 : 1) * HOUR;
    let e = execT();
    add('DONE', e);
    const retP = ex.formal && e < 15 * MIN ? 0.38 : 0.05;
    if (R.chance(retP)) {
      add('IN_PROGRESS', R.between(1, 10) * HOUR); q.returns = 1;
      add('DONE', execT() * 1.5);
    }
    add('CLOSED', R.logn(5, 0.8) * HOUR);
    q.sts = st;
  }
  // Обработка с повторами: после закрытия неисправность может вернуться на тот же вагон
  for (let i = 0; i < REQS.length; i++) {
    const q = REQS[i];
    lifecycle(q);
    const last = q.sts[q.sts.length - 1];
    if (last.s !== 'CLOSED' || q.svc === 'E') continue;
    const ex = UBY[q.executor];
    let p = q.svc === 'C' ? 0.13 : 0.09;
    if (ex && ex.formal) p = 0.32;
    else if (q.depot === 4) p = 0.17;
    if (q.repeatOf) p *= 0.5;
    if (!R.chance(p)) continue;
    const ct = last.t + R.exp(q.svc === 'C' ? 6 : 14) * DAY;
    if (ct > NOW) continue;
    const w = WBY[q.wagon];
    const rc = R.pick(RECEIVERS[w.depot]);
    newReq({ wagon: q.wagon, trip: null, created: ct, received: ct + MIN, fault: q.fault, sev: q.sev, author: rc.id, role: rc.role, src: 'manual', repeatOf: q.id });
  }
  // Срез на текущий момент: события после NOW отбрасываются
  for (const q of REQS) {
    q.sts = q.sts.filter(s => s.t <= NOW);
    if (!q.sts.length) q.future = true;
    q.status = q.sts.length ? q.sts[q.sts.length - 1].s : 'NEW';
    q.open = !['CLOSED', 'REJECTED', 'CANCELLED'].includes(q.status);
    q.statusFrom = q.sts.length ? q.sts[q.sts.length - 1].t : q.created;
  }
  const REQ_LIST = REQS.filter(q => !q.future && q.created <= NOW).sort((a, b) => a.created - b.created);

  /* ---------- Журнал состояния вагона (wagon_status) по суткам ---------- */
  const STATE = {
    IN_TRIP: { name: 'В пути', color: 'var(--s1)' }, IN_IDLE: { name: 'В простое', color: 'var(--s2)' },
    RESERVE: { name: 'Резерв', color: 'var(--s3)' }, IN_REPAIR: { name: 'В ремонте', color: 'var(--s4)' },
    OUT_OF_SERVICE: { name: 'Отставлен', color: 'var(--s5)' }
  };
  function intervals(w) {
    if (w._iv) return w._iv;
    const iv = [];
    for (const id of w.trips) { const t = TRIPS[id - 1]; iv.push([t.dep, Math.min(t.arr, NOW), 'IN_TRIP']); }
    for (const rp of w.repairs) iv.push([rp.from, Math.min(rp.to, NOW), 'IN_REPAIR', rp]);
    iv.sort((a, b) => a[0] - b[0]);
    w._iv = iv;
    return iv;
  }
  function stateAt(w, t) {
    if (w.excludedFrom && t >= w.excludedFrom) return 'OUT_OF_SERVICE';
    const iv = intervals(w);
    let lo = 0, hi = iv.length - 1;
    while (lo <= hi) {
      const m = (lo + hi) >> 1;
      if (iv[m][1] <= t) lo = m + 1; else if (iv[m][0] > t) hi = m - 1; else return iv[m][2];
    }
    if (w.forgottenFrom && t >= w.forgottenFrom) return 'IN_IDLE';
    return w.spare ? 'RESERVE' : 'IN_IDLE';
  }
  /* Где стоит вагон: станция, депо, парк отстоя, рейс (wagon_location) - упрощённо */
  function locAt(w, t) {
    const s = stateAt(w, t);
    if (s === 'IN_TRIP') return 'TRIP';
    if (s === 'IN_REPAIR') return 'DEPOT';
    if (w.forgottenFrom && t >= w.forgottenFrom) return 'YARD';
    if (s === 'RESERVE' || s === 'OUT_OF_SERVICE') return 'YARD';
    return 'STATION';
  }

  return {
    R, START, DEPOTS, DEP, TYPES, MODELS, SERVICES, SEV, STATUS, FG, FGBY, FAULTS, FBY, ROUTES, RBY, WAGONS, WBY, TRAINS,
    USERS, UBY, CREW, EXEC, RECEIVERS, TRIPS, REQS: REQ_LIST, ALL_REQS: REQS, CHECKS, EQUIPS, CHECK_ITEMS, EQUIP_ITEMS, REPAIRS,
    STATE, stateAt, locAt, intervals, kmAt, minAt, inSec, season, COLD, HOLIDAYS, stName, COUPLINGS
  };
})();
