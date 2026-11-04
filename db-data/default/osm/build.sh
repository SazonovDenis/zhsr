#!/bin/bash
# Сборка station.tsv, stage.tsv, stage_point.tsv из выгрузки fetch.sh и сверка с текущими db-data/default.
#   build.sh [каталог выгрузки]          - собрать в <каталог>/out и сверить, db-data/default не трогать;
#   build.sh [каталог выгрузки] --apply  - то же, затем скопировать в db-data/default.
set -eu
here=$(cd "$(dirname "$0")" && pwd)
data=$(cd "$here/.." && pwd)
work=$(cd "$here/../../.." && pwd)/build/osm
apply=0
for a in "$@"; do
  case $a in
    --apply) apply=1 ;;
    *) work=$a ;;
  esac
done
mkdir -p "$work/out"

python3 -I "$here/merge_tiles.py" "$work/tiles" "$work/rail.json"
python3 -I "$here/build_graph.py" "$work/rail.json" "$work/kz.json" "$work/out"
echo '--- сверка с db-data/default'
python3 -I "$here/check_km.py" "$data" "$work/out"

if [ $apply = 1 ]; then
  cp "$work/out/station.tsv" "$data/01-station.tsv"
  cp "$work/out/stage.tsv" "$data/02-stage.tsv"
  cp "$work/out/stage_point.tsv" "$data/03-stage_point.tsv"
  echo "db-data/default обновлены; id станций новые - перепривязать станции в db-data/demo"
fi
