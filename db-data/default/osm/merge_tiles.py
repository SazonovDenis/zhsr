"""Склеить выгрузки tiles/t*.json в один файл: элемент с тегами важнее элемента без тегов. argv: каталог, итоговый файл."""
import glob
import json
import sys

src, out = sys.argv[1:3]
els = {}
for p in sorted(glob.glob(f'{src}/t*.json')):
    for e in json.load(open(p))['elements']:
        k = (e['type'], e['id'])
        if k not in els or ('tags' in e and 'tags' not in els[k]):
            els[k] = e
json.dump({'elements': list(els.values())}, open(out, 'w'))
print(len(els))
