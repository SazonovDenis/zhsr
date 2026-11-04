"""Сверка нового графа с прежним: для каждого прежнего перегона - кратчайший путь между теми же станциями в новом.

argv: каталог прежних 01-station.tsv и 02-stage.tsv (db-data/default), каталог новых station.tsv и stage.tsv. Станции сопоставляются по коду, затем по имени.
Строки с расхождением больше 6% помечены <<<; None - станция не найдена или пути нет.
"""
import csv
import heapq
import sys
from collections import defaultdict


def read(path):
    return list(csv.DictReader(open(path, encoding='utf-8'), delimiter='\t'))


old_dir, new_dir = sys.argv[1:3]
old = {r['id']: r for r in read(f'{old_dir}/01-station.tsv')}
new = read(f'{new_dir}/station.tsv')
by_code = {r['code']: r['id'] for r in new}
by_name = defaultdict(set)
for r in new:
    by_name[r['name']].add(r['id'])


def match(r):
    return {by_code[r['code']]} if r['code'] in by_code else by_name.get(r['name'], set())


adj = defaultdict(list)
for r in read(f'{new_dir}/stage.tsv'):
    a, b, km = r['from_station'], r['to_station'], int(r['km'])
    adj[a].append((b, km))
    adj[b].append((a, km))


def shortest(src, dst):
    dm = {s: 0 for s in src}
    heap = [(0, s) for s in src]
    while heap:
        d, x = heapq.heappop(heap)
        if x in dst:
            return d
        if d > dm[x]:
            continue
        for y, w in adj[x]:
            if d + w < dm.get(y, 1 << 60):
                dm[y] = d + w
                heapq.heappush(heap, (d + w, y))
    return None


missing = sorted({r['name'] for r in old.values() if not match(r)})
print('не найдены:', missing)
for r in read(f'{old_dir}/02-stage.tsv'):
    a, b = match(old[r['from_station']]), match(old[r['to_station']])
    km = int(r['km'])
    got = shortest(a, b) if a and b else None
    bad = got is None or abs(got - km) > 0.06 * km
    print(f"{km:5} {str(got):>5} {'<<<' if bad else '   '} {old[r['from_station']]['name']} - {old[r['to_station']]['name']}")
