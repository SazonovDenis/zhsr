-- Демо-данные записаны на момент 2026-10-29 07:05 по времени Казахстана (+05): рейс 002 в пути, заявки поданы
-- «час назад». Сдвигаем всё время в таблицах так, чтобы этот момент стал текущим: данные всегда свежие.
-- Даты (date: период действия маршрута, ввод вагона в эксплуатацию) не сдвигаются.
--
-- Сдвигаются все колонки timestamptz во всех таблицах, кроме служебных таблиц Liquibase: после install/db-reset время есть
-- только в демо-данных. Таблицы с журналами (колонка dbeg) - построчно от поздних записей к ранним: иначе сдвинутая
-- запись на время перекроется с ещё не сдвинутой следующей, и ограничение exclude на пересечение периодов сработает.
do $$
declare
    shift interval := date_trunc('minute', now()) - timestamptz '2026-10-29 07:05+05';
    tab record;
    rec record;
begin
    for tab in
        select c.table_name, string_agg(format('%1$I = %1$I + $1', c.column_name), ', ') as sets,
               bool_or(c.column_name = 'dbeg')
                   and exists (select from information_schema.columns i
                               where i.table_schema = c.table_schema and i.table_name = c.table_name
                                 and i.column_name = 'id') as journal
        from information_schema.columns c
        where c.table_schema = current_schema()
          and c.data_type = 'timestamp with time zone'
          and c.table_name not like 'databasechangelog%'
        group by c.table_schema, c.table_name
    loop
        if tab.journal then
            for rec in execute format('select id from %I order by dbeg desc', tab.table_name) loop
                execute format('update %I set %s where id = $2', tab.table_name, tab.sets) using shift, rec.id;
            end loop;
        else
            execute format('update %I set %s', tab.table_name, tab.sets) using shift;
        end if;
    end loop;
end
$$;
