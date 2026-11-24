"""Станции, перегоны и точки перегонов Казахстана из OpenStreetMap (выгрузки Overpass).

argv: rail.json (пути и станции в прямоугольнике вокруг Казахстана), kz.json (станции внутри Казахстана),
каталог для station.tsv, station_point.tsv, stage.tsv, stage_point.tsv. Подробно - README.md рядом.

- Станция: node railway=station, кроме метро и LRT. Дубли (один код ЕСР или одно имя ближе DUP_KM) склеиваются.
- Разрывы путей в OSM (концы соседних путей не в одной точке) склеиваются, если ближе GAP_KM.
- Узлы путей ближе OWN_KM принадлежат ближайшей станции.
- Перегон: кратчайший путь по путям от станции A до первой встреченной станции B; км - от точки станции до точки
  станции.
- Станции вне Казахстана берутся только как транзитные: цепочка таких станций между двумя станциями сети не длиннее
  TRANSIT_KM, и без неё путь между ними по сети длиннее в TRANSIT_GAIN раз или его нет (Уральск через Илецк,
  Мактаарал через Узбекистан). Цепочки печатаются - их надо просмотреть.
- Станции без имени (OSM <id>) и с именем-километром («117 км», «рзд. 142 км») убираются: у промежуточной соседние
  перегоны склеиваются в один, тупиковая удаляется с перегоном, развилка (три перегона и больше) остаётся с именем
  «Развилка XXX/YYY» по двум ближайшим соседним станциям.
- Станции с номером вместо имени («№ 1», «рзд. №33», «19») получают имя «Разъезд A-B» по соседним станциям.
"""
import heapq
import json
import math
import re
import sys
from collections import defaultdict

OWN_KM = 1.2         # радиус станции
SNAP_KM = 2.0        # станция дальше от путей отбрасывается
DUP_KM = 2.0         # точки станции с одним именем ближе - одна станция
GAP_KM = 0.05        # склейка разрывов путей
TRANSIT_KM = 300     # предел длины цепочки транзитных станций; 600 уже тянет обходы через Курган, Омск, Китай
TRANSIT_GAIN = 1.2
SIMPLIFY_KM = 0.05   # точность линии перегона для stage_point

src, kz_src, out = sys.argv[1:4]
els = json.load(open(src))['elements']
kz_ids = {e['id'] for e in json.load(open(kz_src))['elements']}

coord, stations, ways = {}, [], []
for e in els:
    if e['type'] == 'node':
        coord[e['id']] = (e['lon'], e['lat'])
        t = e.get('tags')
        if t and t.get('railway') == 'station' and t.get('station') not in ('subway', 'light_rail') \
                and t.get('subway') != 'yes' and t.get('light_rail') != 'yes':
            stations.append(e)
    elif e['type'] == 'way':
        ways.append(e['nodes'])


def dist(a, b):
    (lon1, lat1), (lon2, lat2) = a, b
    p1, p2 = math.radians(lat1), math.radians(lat2)
    h = math.sin((p2 - p1) / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(math.radians(lon2 - lon1) / 2) ** 2
    return 2 * 6371.0088 * math.asin(math.sqrt(h))


# устаревшие или неточные name:ru, имена латиницей
NAME_FIX = {'Чимкент': 'Шымкент', 'Аягуз': 'Аягоз', 'Rzd.jarkij': 'Разъезд Жаркий',
            'разъезд 87 Алтыкудык': 'Разъезд Алтыкудык'}
NAME_PREFIX = ('Железнодорожный вокзал ', 'Станция ')


def name_of(t):
    n = t.get('name:ru') or t.get('name') or t.get('uic_name') or ''
    for p in NAME_PREFIX:
        if n.startswith(p):
            n = n[len(p):]
    return NAME_FIX.get(n, n)


# --- склейка дублей станций
st = {s['id']: s for s in stations}
rep = {}


def root(x):
    while rep.get(x, x) != x:
        x = rep[x]
    return x


def union(a, b):
    a, b = root(a), root(b)
    if a == b:
        return
    # представитель: с кодом ЕСР, затем меньший id
    ka = ('esr:user' not in st[a]['tags'], a)
    kb = ('esr:user' not in st[b]['tags'], b)
    if kb < ka:
        a, b = b, a
    rep[b] = a


by_esr = defaultdict(list)
by_name = defaultdict(list)
for s in stations:
    t = s['tags']
    if 'esr:user' in t:
        by_esr[t['esr:user']].append(s['id'])
    n = name_of(t).lower().replace('ё', 'е')
    if n:
        by_name[n].append(s['id'])
for ids in by_esr.values():
    for i in ids[1:]:
        union(ids[0], i)
for ids in by_name.values():
    for i in range(len(ids)):
        for j in range(i + 1, len(ids)):
            if dist(coord[ids[i]], coord[ids[j]]) <= DUP_KM:
                union(ids[i], ids[j])
group_kz = defaultdict(bool)
for s in stations:
    if s['id'] in kz_ids:
        group_kz[root(s['id'])] = True

# --- граф путей
adj = defaultdict(list)
way_of = defaultdict(set)
for wi, nodes in enumerate(ways):
    for a, b in zip(nodes, nodes[1:]):
        d = dist(coord[a], coord[b])
        adj[a].append((b, d))
        adj[b].append((a, d))
    for n in nodes:
        way_of[n].add(wi)

CELL = 0.05
grid = defaultdict(list)
for n in adj:
    lon, lat = coord[n]
    grid[(int(lon // CELL), int(lat // CELL))].append(n)


def near(pt, km):
    lon, lat = pt
    r = 1 + int(km / 111 / CELL / max(math.cos(math.radians(lat)), 0.3))
    cx, cy = int(lon // CELL), int(lat // CELL)
    for x in range(cx - r, cx + r + 1):
        for y in range(cy - r, cy + r + 1):
            for n in grid.get((x, y), ()):
                d = dist(pt, coord[n])
                if d <= km:
                    yield n, d


healed = 0
for n in [n for n in adj if len(adj[n]) == 1]:
    cand = [(d, m) for m, d in near(coord[n], GAP_KM) if m != n and not (way_of[m] & way_of[n])]
    if cand:
        d, m = min(cand)
        adj[n].append((m, d))
        adj[m].append((n, d))
        healed += 1

# --- узлы станций; станция-группа владеет узлами вокруг всех своих точек
owner, best = {}, {}
for s in stations:
    g = root(s['id'])
    found = list(near(coord[s['id']], SNAP_KM))
    if not found:
        continue
    nearest = min(found, key=lambda x: x[1])[0]
    for n, d in found:
        if (d <= OWN_KM or n == nearest) and d < best.get(n, math.inf):
            best[n] = d
            owner[n] = g
own_nodes = defaultdict(list)
for n, g in owner.items():
    own_nodes[g].append(n)
pt = {g: coord[g] for g in own_nodes}

# --- перегоны между всеми станциями
stage = {}
for a in own_nodes:
    distm, prev, heap = {}, {}, []
    for n in own_nodes[a]:
        distm[n] = dist(pt[a], coord[n])
        heap.append((distm[n], n))
    heapq.heapify(heap)
    while heap:
        d, n = heapq.heappop(heap)
        if d > distm[n]:
            continue
        o = owner.get(n)
        if o is not None and o != a:
            key = (min(a, o), max(a, o))
            d += dist(coord[n], pt[o])
            if key not in stage or d < stage[key][0]:
                path = [n]
                while path[-1] in prev:
                    path.append(prev[path[-1]])
                path.reverse()  # от A к B
                stage[key] = (d, path if a == key[0] else path[::-1])
            continue
        for m, w in adj[n]:
            nd = d + w
            if nd < distm.get(m, math.inf):
                distm[m] = nd
                prev[m] = n
                heapq.heappush(heap, (nd, m))

g_adj = defaultdict(list)
for (a, b), (km, _) in stage.items():
    g_adj[a].append((b, km))
    g_adj[b].append((a, km))

# --- транзит: связываем части сети Казахстана через станции вне Казахстана
kz = {g for g in own_nodes if group_kz[g]}
comp = {}


def relabel():
    comp.clear()
    for s in kz:
        if s in comp:
            continue
        comp[s] = s
        stack = [s]
        while stack:
            x = stack.pop()
            for y, _ in g_adj[x]:
                if y in kz and y not in comp:
                    comp[y] = s
                    stack.append(y)


made_name = {}  # имена вместо номеров и развилок, см. ниже


def sname(g):
    return made_name.get(g) or name_of(st[g]['tags']) or f'OSM {g}'


def kz_dist(a, b, limit):
    """Кратчайшее расстояние между станциями a и b только по станциям сети; дальше limit - inf."""
    dm, heap = {a: 0.0}, [(0.0, a)]
    while heap:
        d, x = heapq.heappop(heap)
        if x == b:
            return d
        if d > dm[x] or d > limit:
            continue
        for y, w in g_adj[x]:
            if y in kz and d + w < dm.get(y, math.inf):
                dm[y] = d + w
                heapq.heappush(heap, (d + w, y))
    return math.inf


# цепочки через станции вне Казахстана: от станции сети до другой станции сети
cand = []
for a in kz:
    if all(y in kz for y, _ in g_adj[a]):
        continue
    dm, prev, heap = {a: 0.0}, {}, [(0.0, a)]
    while heap:
        d, x = heapq.heappop(heap)
        if d > dm[x] or d > TRANSIT_KM:
            continue
        if x != a and x in kz:
            if a < x:
                path = [x]
                while path[-1] in prev:
                    path.append(prev[path[-1]])
                cand.append((d, a, x, path[1:-1]))
            continue
        for y, w in g_adj[x]:
            if d + w < dm.get(y, math.inf):
                dm[y] = d + w
                prev[y] = x
                heapq.heappush(heap, (d + w, y))

# цепочка берётся, если без неё путь между её концами по сети длиннее в TRANSIT_GAIN раз или его нет
transit = set()
for d, a, b, foreign in sorted(cand):
    if kz_dist(a, b, d * TRANSIT_GAIN) > d * TRANSIT_GAIN:
        transit.update(foreign)
        print(f'  цепочка {round(d)} км, {len(foreign)} ст.: {sname(a)} - {sname(b)}: {", ".join(sname(x) for x in foreign[:6])}')
        kz.update(foreign)  # дальше считаются частью сети

keep = {s for s in kz if any(y in kz for y, _ in g_adj[s])}
rows = {k: v for k, v in stage.items() if k[0] in keep and k[1] in keep}

# --- станции без имени и с именем-километром: промежуточные склеиваются, тупиковые удаляются, развилки остаются
KM_NAME = re.compile(r'\d+\s*км$', re.IGNORECASE)


def unnamed(g):
    n = name_of(st[g]['tags']).strip()
    return not n or bool(KM_NAME.search(n))


def contract(rows, keep, drop):
    """Убрать из rows (перегоны) и keep (станции) станции drop, у которых не больше двух перегонов; вернуть
    (склеено, удалено тупиков). Снятие станции меняет число перегонов у соседей - повторяется, пока есть что убирать."""
    joined = dead = 0
    nb = defaultdict(set)
    for a, b in rows:
        nb[a].add(b)
        nb[b].add(a)
    queue = sorted(drop & keep)
    while queue:
        s = queue.pop()
        # тупик на единственном перегоне соседа не удаляется: сосед остался бы без перегонов
        if s not in keep or len(nb[s]) > 2 or len(nb[s]) == 1 and len(nb[next(iter(nb[s]))]) == 1:
            continue
        ends = sorted(nb[s])
        legs = []
        for e in ends:
            km, path = rows.pop((min(e, s), max(e, s)))
            legs.append((km, path if e < s else path[::-1]))  # путь от соседа к s
            nb[e].discard(s)
        keep.discard(s)
        del nb[s]
        if len(ends) == 2:
            a, b = ends
            km = legs[0][0] + legs[1][0]
            # параллельный перегон между теми же станциями: остаётся короткий
            if (a, b) not in rows or km < rows[(a, b)][0]:
                rows[(a, b)] = (km, legs[0][1] + [s] + legs[1][1][::-1])
            nb[a].add(b)
            nb[b].add(a)
            joined += 1
        else:
            dead += 1
        queue += [e for e in ends if e in drop]
    return joined, dead


drop = {g for g in keep if unnamed(g)}
joined, dead = contract(rows, keep, drop)
transit &= keep
print('без имени и километровых', len(drop), 'склеено', joined, 'удалено тупиков', dead)

# --- имена: развилки и станции, у которых вместо имени номер («№ 1», «19», «рзд. №33», «Razezd No 27», «Разъезд»)
NUMBER_NAME = re.compile(
    r"^(?:(?:(?:жд\s+)?(?:раз[ъь]езд|рзд\.?|раз\.?|raz'?ezd|обгонный пункт)\s*)?(?:№|no\.?)?\s*\d+(?:\s*-?\s*[а-яa-z])?"
    r"(?:-темір жол айрығы|\s+остановочный пункт)?|раз[ъь]езд)$", re.IGNORECASE)


def make_names(rows, weak, name):
    """Имена станций weak: развилка (три перегона и больше) - «Развилка A/B» по двум ближайшим станциям с именем;
    остальные - «Разъезд A-B» по ближайшим станциям с именем по обе стороны (по алфавиту), несколько подряд -
    с номером от A; у тупика - «Разъезд A». name(g) - прежнее имя."""
    nb = defaultdict(dict)
    for (a, b), (km, _) in rows.items():
        nb[a][b] = km
        nb[b][a] = km
    res = {}

    def nearby(g):
        return [n for _, n in sorted((km, name(x)) for x, km in nb[g].items() if x not in weak)]

    for g in weak:
        if len(nb[g]) > 2 and len(nearby(g)) > 1:
            res[g] = 'Развилка ' + '/'.join(nearby(g)[:2])

    def end_name(g):
        # конец цепочки - развилка: вместо неё ближайшая к ней станция с именем
        return nearby(g)[0] if g in weak and nearby(g) else name(g)

    seen = set()
    for s in sorted(g for g in weak if len(nb[g]) <= 2):
        if s in seen:
            continue
        # цепочка станций без имени: от s в обе стороны до станции с именем, развилки или тупика
        sides, ends = [], []
        for x in sorted(nb[s]):
            prev, side = s, []
            while x in weak and len(nb[x]) <= 2 and x != s:
                side.append(x)
                onward = [y for y in nb[x] if y != prev]
                prev = x
                if not onward:
                    x = None
                    break
                x = onward[0]
            sides.append(side)
            ends.append(end_name(x) if x is not None else None)
        chain = (sides[0][::-1] if sides else []) + [s] + (sides[1] if len(sides) > 1 else [])
        seen.update(chain)
        names = [n for n in ends if n]
        if len(names) == 2 and names[0] > names[1]:
            names.reverse()
            chain.reverse()
        for k, g in enumerate(chain, 1):
            if names:
                res[g] = 'Разъезд ' + '-'.join(names) + (f' {k}' if len(chain) > 1 else '')
    return res


weak = drop & keep | {g for g in keep if NUMBER_NAME.match(name_of(st[g]['tags']).strip())}
for g, n in sorted(make_names(rows, weak, sname).items(), key=lambda kv: kv[1]):
    print('  имя:', sname(g), '->', n)
    made_name[g] = n


def simplify(pts, eps):
    if len(pts) < 3:
        return pts
    lat0 = math.radians(sum(p[1] for p in pts) / len(pts))
    xy = [(p[0] * 111.32 * math.cos(lat0), p[1] * 110.57) for p in pts]
    keep_ = [False] * len(pts)
    keep_[0] = keep_[-1] = True
    stack = [(0, len(pts) - 1)]
    while stack:
        i, j = stack.pop()
        (x1, y1), (x2, y2) = xy[i], xy[j]
        dx, dy = x2 - x1, y2 - y1
        ln = math.hypot(dx, dy) or 1e-9
        k, dmax = -1, eps
        for m in range(i + 1, j):
            x, y = xy[m]
            dd = abs(dy * (x - x1) - dx * (y - y1)) / ln
            if dd > dmax:
                k, dmax = m, dd
        if k >= 0:
            keep_[k] = True
            stack += [(i, k), (k, j)]
    return [p for p, f in zip(pts, keep_) if f]


order = sorted(keep, key=lambda g: (sname(g), g))
sid = {g: i + 1 for i, g in enumerate(order)}
npts = 0
ordered = sorted(rows.items(), key=lambda kv: tuple(sorted((sid[kv[0][0]], sid[kv[0][1]]))))
with open(f'{out}/stage.tsv', 'w', encoding='utf-8') as f, \
        open(f'{out}/stage_point.tsv', 'w', encoding='utf-8') as fp:
    f.write('id\tfrom_station\tto_station\tkm\tdescription\n')
    fp.write('id\tstage\tseq\tx\ty\n')
    for i, ((a, b), (km, path)) in enumerate(ordered, 1):
        pts = [pt[a]] + [coord[n] for n in path] + [pt[b]]
        if sid[a] > sid[b]:
            a, b = b, a
            pts.reverse()
        f.write(f"{i}\t{sid[a]}\t{sid[b]}\t{max(1, round(km))}\t{sname(a)} - {sname(b)}\n")
        line = simplify(pts, SIMPLIFY_KM)
        for seq, (x, y) in enumerate(line, 1):
            npts += 1
            fp.write(f"{npts}\t{i}\t{seq}\t{x:.6f}\t{y:.6f}\n")

with open(f'{out}/station.tsv', 'w', encoding='utf-8') as f, \
        open(f'{out}/station_point.tsv', 'w', encoding='utf-8') as fp:
    f.write('id\tcode\tname\tdescription\n')
    fp.write('id\tstation\tx\ty\n')
    for g in order:
        t = st[g]['tags']
        merged = sorted(s for s in st if root(s) == g and s != g)
        desc = [f'OSM node {g}']
        if merged:
            desc.append('склеены ' + ', '.join(map(str, merged)))
        if 'uic_ref' in t:
            desc.append(f"uic_ref {t['uic_ref']}")
        if g in transit:
            desc.append('вне Казахстана, транзит')
        f.write(f"{sid[g]}\t{t.get('esr:user') or f'OSM{g}'}\t{sname(g)}\t{'; '.join(desc)}\n")
        fp.write(f"{sid[g]}\t{sid[g]}\t{pt[g][0]:.6f}\t{pt[g][1]:.6f}\n")

relabel()
sizes = defaultdict(int)
for s in keep:
    sizes[comp.get(s)] += 1
print('станций OSM', len(stations), 'склеено дублей', len(rep), 'разрывов', healed)
print('станций', len(keep), 'из них транзит', len(transit), 'перегонов', len(rows), 'точек', npts,
      'км', round(sum(v[0] for v in rows.values())))
print('части сети', sorted(sizes.values(), reverse=True))
print('без ЕСР', sum(1 for g in keep if 'esr:user' not in st[g]['tags']))
print('транзит:', sorted(sname(g) for g in transit))
