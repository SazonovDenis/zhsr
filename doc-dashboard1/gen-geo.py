"""Строит doc-dashboard1/geo.js: упрощённая сеть перегонов, крупные станции, маршруты с километражем."""
import csv, json, heapq, math, sys
from collections import defaultdict

import os
DB = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'db-data') + '/'
OUT = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(os.path.abspath(__file__)), 'geo.js')


def rd(p):
    return list(csv.DictReader(open(DB + p, encoding='utf-8'), delimiter='\t'))


st = {r['id']: r for r in rd('default/01-station.tsv')}
by_name = {}
for r in st.values():
    by_name.setdefault(r['name'], r['id'])
pts = defaultdict(list)
for p in rd('default/03-stage_point.tsv'):
    pts[p['stage']].append((int(p['seq']), float(p['x']), float(p['y'])))
for k in pts:
    pts[k].sort()
stages = rd('default/02-stage.tsv')
sp_xy = {}
for k, v in pts.items():
    pass
sp_by_id = {}
for p in rd('default/03-stage_point.tsv'):
    sp_by_id[p['id']] = (float(p['x']), float(p['y']))


def st_xy(sid):
    return sp_by_id.get(st[sid]['stage_point'])


def dp(points, eps):
    if len(points) < 3:
        return points
    (x1, y1), (x2, y2) = points[0], points[-1]
    dx, dy = x2 - x1, y2 - y1
    L = math.hypot(dx, dy) or 1e-9
    best, idx = 0, 0
    for i in range(1, len(points) - 1):
        x, y = points[i]
        d = abs(dy * x - dx * y + x2 * y1 - y2 * x1) / L
        if d > best:
            best, idx = d, i
    if best > eps:
        return dp(points[:idx + 1], eps)[:-1] + dp(points[idx:], eps)
    return [points[0], points[-1]]


graph = defaultdict(list)
stage_of = {}
for s in stages:
    a, b, km = s['from_station'], s['to_station'], int(s['km'])
    graph[a].append((b, km))
    graph[b].append((a, km))
    stage_of[frozenset((a, b))] = s


def stage_line(a, b):
    s = stage_of[frozenset((a, b))]
    line = [(x, y) for _, x, y in pts[s['id']]]
    if s['from_station'] != a:
        line = line[::-1]
    return line


# Сеть: упрощённые линии перегонов, склеенные в цепочки для компактности
net = []
for s in stages:
    line = [(x, y) for _, x, y in pts[s['id']]]
    line = dp(line, 0.02)
    net.append([round(c, 3) for xy in line for c in xy])

def path(a, b):
    dist = {a: 0}
    prev = {}
    q = [(0, a)]
    while q:
        d, u = heapq.heappop(q)
        if u == b:
            break
        if d > dist[u]:
            continue
        for v, w in graph[u]:
            nd = d + w
            if nd < dist.get(v, 1e18):
                dist[v] = nd
                prev[v] = u
                heapq.heappush(q, (nd, v))
    seq = [b]
    while seq[-1] != a:
        seq.append(prev[seq[-1]])
    return seq[::-1]


def route_from_db(rid):
    rs = sorted([r for r in rd('demo/11-route_station.tsv') if r['route'] == rid], key=lambda r: int(r['seq']))
    return [(r['station'], int(r['km']), r['have_stop'] == 'true',
             int(r['arrival_min']) if r['arrival_min'] else None,
             int(r['departure_min']) if r['departure_min'] else None) for r in rs]


def route_synth(names, dep_min, speed=62):
    ids = [by_name[n] for n in names]
    seq = [ids[0]]
    for a, b in zip(ids, ids[1:]):
        seq += path(a, b)[1:]
    out, km, t = [], 0, dep_min
    for i, sid in enumerate(seq):
        if i:
            km += next(w for v, w in graph[seq[i - 1]] if v == sid)
        stop = sid in ids
        arr = None if i == 0 else round(dep_min + km / speed * 60 + (t - dep_min))
        dwell = 10 if stop and 0 < i < len(seq) - 1 else 0
        dep = None if i == len(seq) - 1 else (arr or dep_min) + dwell
        if dwell:
            t += dwell
        out.append((sid, km, stop, arr, dep))
    return out


ROUTES = [
    ('1', '1/2', 'Астана — Алматы', route_from_db('1')),
    ('103', '9/10', 'Астана-1 — Алматы-2', route_from_db('103')),
    ('3', '77/78', 'Астана — Петропавловск — Астана', route_from_db('3')),
    ('101', '655/656', 'Нурлы Жол — Аркалык — Нурлы Жол', route_from_db('101')),
    ('102', '659/660', 'Нурлы Жол — Айсары — Нурлы Жол', route_from_db('102')),
    ('201', '51/52', 'Алматы — Шымкент', route_synth(['Алматы-2', 'Шу', 'Тараз', 'Шымкент'], 1290)),
    ('202', '13/14', 'Астана — Павлодар', route_synth(['Астана-1', 'Экибастуз 3', 'Павлодар'], 1320)),
    ('203', '39/40', 'Алматы — Усть-Каменогорск', route_synth(['Алматы-2', 'Актогай', 'Аягоз', 'Семей', 'Усть-Каменогорск'], 1110)),
    ('204', '71/72', 'Шымкент — Кызылорда', route_synth(['Шымкент', 'Туркестан', 'Кызылорда'], 420)),
    ('205', '23/24', 'Астана — Костанай', route_synth(['Астана-1', 'Костанай'], 1230)),
]

segs = {}
routes = []
for rid, num, name, seq in ROUTES:
    line = []
    for (a, *_), (b, *_) in zip(seq, seq[1:]):
        seg = stage_line(a, b)
        line += seg if not line else seg[1:]
        key = '%s-%s' % (min(int(a), int(b)), max(int(a), int(b)))
        if key not in segs:
            sl = seg if int(a) < int(b) else seg[::-1]
            segs[key] = [round(c, 3) for xy in dp(sl, 0.01) for c in xy]
    line = dp(line, 0.01)
    routes.append({
        'id': int(rid), 'num': num, 'name': name,
        'st': [[int(s), km, 1 if stop else 0, arr, dep] for s, km, stop, arr, dep in seq],
        'line': [round(c, 3) for xy in line for c in xy],
    })

used = {s[0] for r in routes for s in r['st']}
names = {}
for sid in used:
    xy = st_xy(str(sid))
    names[sid] = [st[str(sid)]['name'], round(xy[0], 3), round(xy[1], 3)]
MAJOR = ['Астана-1', 'Алматы-2', 'Караганда', 'Петропавловск', 'Кокшетау I', 'Нурлы Жол', 'Аркалык', 'Айсары',
         'Шымкент', 'Тараз', 'Шу', 'Сары-Шаган', 'Балхаш 1', 'Павлодар', 'Экибастуз 3', 'Семей', 'Усть-Каменогорск',
         'Актогай', 'Аягоз', 'Кызылорда', 'Туркестан', 'Костанай', 'Атырау', 'Уральск', 'Бейнеу', 'Мангистау',
         'Аральское Море', 'Мойынты', 'Атбасар-1', 'Есиль-1', 'Шортанды']
major = []
for n in MAJOR:
    sid = by_name.get(n)
    if sid and st_xy(sid):
        x, y = st_xy(sid)
        major.append([int(sid), n, round(x, 3), round(y, 3)])
for n in MAJOR:
    if n not in by_name:
        print('нет станции', n, file=sys.stderr)

with open(OUT, 'w', encoding='utf-8') as f:
    f.write('/* Сгенерировано из db-data/default (перегоны, точки перегонов, станции) и db-data/demo/11-route_station.tsv.\n')
    f.write(' * Маршруты 201-205 построены кратчайшим путём по графу перегонов: в демо-данных их нет. */\n')
    f.write("'use strict';\n")
    f.write('const GEO = ')
    for r in routes:
        del r['line']
    json.dump({'net': net, 'major': major, 'stations': names, 'routes': routes, 'seg': segs}, f, ensure_ascii=False,
              separators=(',', ':'))
    f.write(';\n')
print('net', len(net), 'points', sum(len(x) for x in net) // 2, 'routes', len(routes))
