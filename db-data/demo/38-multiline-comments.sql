-- Многострочные описания: tsv грузится как csv без кавычек, перевод строки в поле не записать - в tsv он записан двумя
-- символами \n. Здесь \n заменяется настоящим переводом строки (строки SQL - стандартные: '\n' - обратная косая и n).
update check_list_value set comment = replace(comment, '\n', e'\n') where strpos(comment, '\n') > 0;
update equip_list_value set comment = replace(comment, '\n', e'\n') where strpos(comment, '\n') > 0;
