#!/bin/bash
# Выгрузка из Overpass API в каталог $1 (по умолчанию build/osm от корня проекта):
#   kz.json         - станции и остановочные пункты внутри Казахстана (по полигону страны, relation 214665);
#   tiles/t0..t5    - пути railway=rail без service=* и станции в прямоугольнике вокруг Казахстана,
#                     шесть полос по долготе: целиком сервер не отдаёт.
# Готовые файлы не перекачиваются: упавшую часть достаточно запустить заново.
set -u
root=$(cd "$(dirname "$0")/../../.." && pwd)
work=${1:-$root/build/osm}
mkdir -p "$work/tiles"
cd "$work"

servers=(https://overpass-api.de/api/interpreter https://overpass.kumi.systems/api/interpreter)

ok() { python3 -I -c "import json,sys; json.load(open(sys.argv[1]))" "$1" 2>/dev/null; }

# fetch <файл> <запрос>: до 8 попыток, серверы по очереди, пауза 45 с
fetch() {
  local f=$1 q=$2
  ok "$f" && { echo "$f уже есть"; return 0; }
  for try in 1 2 3 4 5 6 7 8; do
    local s=${servers[$((try % 2))]}
    curl -s -A 'zhsr-osm-import/1.0' --max-time 650 -o "$f" --data-urlencode "data=$q" "$s"
    if ok "$f"; then echo "$f ok $(stat -c %s "$f") байт, $s"; return 0; fi
    echo "$f попытка $try не удалась ($s): $(head -c 100 "$f" | tr '\n' ' ')"
    sleep 45
  done
  echo "$f НЕ СКАЧАН"; return 1
}

fetch kz.json '[out:json][timeout:180];area(3600214665)->.kz;node["railway"~"^(station|halt)$"](area.kz);out ids;'

lons=(46.3 53 60 66 72 79 87.5)
for i in 0 1 2 3 4 5; do
  b="40.4,${lons[$i]},55.6,${lons[$((i+1))]}"
  fetch "tiles/t$i.json" "[out:json][timeout:600];way[\"railway\"=\"rail\"][!\"service\"]($b)->.w;.w out skel;node(w.w);out skel;node[\"railway\"=\"station\"]($b);out;"
done
