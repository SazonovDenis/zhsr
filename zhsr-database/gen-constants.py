"""
Константы системных справочников из src/main/resources/db/dict/<таблица>.tsv - для бэкенда и фронта:

  zhsr-app/src/main/java/kz/zhsr/app/constants/<Класс>.java - класс: TABLE и константы code = id;
  zhsr-web/src/shared/constants/systemDicts.ts             - те же константы и записи справочников: системные
                                                             справочники фронт не загружает с сервера.

    python3 -I zhsr-database/gen-constants.py

Запускать после правки tsv системного справочника. Сгенерированные файлы вручную не правятся. Список таблиц и имена
классов - SYSTEM_DICTS ниже; tsv без таблицы в списке (park_type - только документация) пропускаются. Новый справочник
- добавить сюда и в SystemDicts.CLASSES.
"""

import csv
import json
import os

MODULE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.normpath(os.path.join(MODULE, '..'))
DICT = os.path.join(MODULE, 'src', 'main', 'resources', 'db', 'dict')
JAVA = os.path.join(ROOT, 'zhsr-app', 'src', 'main', 'java', 'kz', 'zhsr', 'app', 'constants')
TS = os.path.join(ROOT, 'zhsr-web', 'src', 'shared', 'constants', 'systemDicts.ts')

# таблица -> (класс констант, заголовок, справочник фронта DictManager или None)
SYSTEM_DICTS = {
    'act_status_type': ('ActStatus', 'Статус акта', None),
    'category_type': ('ConductorCategory', 'Категория проводника', None),
    'coupling_type': ('CouplingType', 'Отцепка или прицепка', None),
    'crew_duty_type': ('CrewDuty', 'Должность члена бригады на рейсе', 'CrewDuty'),
    'fault_category_type': ('FaultCategory', 'Категория неисправности', 'FaultCategory'),
    'location_type': ('LocationType', 'Вид расположения вагона', 'LocationKind'),
    'request_status_type': ('RequestStatus', 'Статус заявки на ремонт', 'RequestStatus'),
    'role_type': ('RoleType', 'Роль сотрудника', 'Role'),
    'service': ('ServiceType', 'Служба', 'Service'),
    'severity_type': ('Severity', 'Критичность неисправности', 'Severity'),
    'transfer_direction_type': ('TransferDirection', 'Направление акта приёма-передачи', None),
    'transport_type': ('TransportType', 'Вид перевозки', 'TransportType'),
    'trip_status_type': ('TripStatus', 'Состояние рейса', 'TripState'),
    'wagon_status_type': ('WagonStatus', 'Состояние вагона', 'WagonState'),
    'work_type': ('WorkType', 'Вид работ', 'WorkType'),
}

# колонки tsv, которые не попадают в записи фронта; остальные, кроме id, text, code, - ссылки (число или null)
SKIP = {'id', 'code', 'text', 'description'}

GENERATED = 'Сгенерировано zhsr-database/gen-constants.py из db/dict/{table}.tsv - не править вручную.'


def read(table):
    with open(os.path.join(DICT, table + '.tsv'), encoding='utf-8', newline='') as f:
        return list(csv.DictReader(f, delimiter='\t', quoting=csv.QUOTE_NONE))


def comment(row):
    return row['text'] + (' - ' + row['description'] if row.get('description') else '')


def java(table, cls, title, rows):
    out = ['package kz.zhsr.app.constants;', '', '/**',
           f' * {title}: id {table} (db/dict/{table}.tsv). Системный справочник: значения меняются только вместе с',
           ' * кодом; при старте {@link SystemDicts} сверяет их с БД.',
           ' * <p>',
           ' * ' + GENERATED.format(table=table),
           ' */',
           f'public final class {cls} {{', '',
           f'    public static final String TABLE = "{table}";', '']
    for r in rows:
        out.append(f'    /** {comment(r)} */')
        out.append(f'    public static final long {r["code"]} = {r["id"]};')
    out += ['', f'    private {cls}() {{', '    }', '', '}', '']
    with open(os.path.join(JAVA, cls + '.java'), 'w', encoding='utf-8') as f:
        f.write('\n'.join(out))


def ts_all(dicts):
    out = ['/**',
           ' * Константы и записи системных справочников: значения меняются только вместе с кодом, поэтому фронт не',
           ' * загружает их с сервера (DictManager берёт записи отсюда). Привязка к значению - по id через константы:',
           ' * TripStatus.IN_PROGRESS.',
           ' *',
           ' * Сгенерировано zhsr-database/gen-constants.py из db/dict/*.tsv - не править вручную.',
           ' */',
           '']
    records = []
    for table, (cls, title, dict_name), rows in dicts:
        out.append(f'/** {title}: id {table} */')
        out.append(f'export const {cls} = {{')
        for r in rows:
            out.append(f'  /** {comment(r)} */')
            out.append(f'  {r["code"]}: {r["id"]},')
        out.append('} as const')
        out.append('')
        if dict_name:
            recs = []
            for r in rows:
                rec = {'id': int(r['id']), 'text': r['text']}
                for k, v in r.items():
                    if k not in SKIP:
                        rec[k] = int(v) if v else None
                recs.append(rec)
            records.append((dict_name, table, recs))
    out.append('/** Записи системных справочников фронта по имени справочника DictManager: id, text (на русском) и ссылки. */')
    out.append('export const SYSTEM_DICT_RECORDS = {')
    for dict_name, table, recs in records:
        out.append(f'  // {table}')
        out.append(f'  {dict_name}: [')
        for rec in recs:
            fields = ', '.join(f'{k}: {json.dumps(v, ensure_ascii=False).replace(chr(34), chr(39))}'
                               if isinstance(v, str) else f'{k}: {"null" if v is None else v}'
                               for k, v in rec.items())
            out.append(f'    {{ {fields} }},')
        out.append('  ],')
    out.append('}')
    out.append('')
    os.makedirs(os.path.dirname(TS), exist_ok=True)
    with open(TS, 'w', encoding='utf-8') as f:
        f.write('\n'.join(out))


def main():
    dicts = []
    for table, spec in SYSTEM_DICTS.items():
        rows = read(table)
        for r in rows:
            if "'" in r['text']:
                raise SystemExit(f'{table}: апостроф в тексте: {r["text"]}')
        java(table, spec[0], spec[1], rows)
        dicts.append((table, spec, rows))
        print(f'{table}: {spec[0]}, {len(rows)}')
    ts_all(dicts)
    print(os.path.relpath(TS, ROOT))


if __name__ == '__main__':
    main()
