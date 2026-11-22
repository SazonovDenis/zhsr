-- Демо: администратор admin (из db-data/default, пароль 123456) получает демо-пароль `qwerty-123456-QAZWSX-!@#$%^` и входит без смены
-- пароля - флаг «сменить пароль» снят.
-- Без --demo пароль и флаг остаются: на рабочей установке пароль по умолчанию меняют при первом входе.
update usr set password_hash = '$2a$10$CmsENnMAm1VlafYZOKkdfu9mHIOmzctPK/zhuae7r382namMrUnDm', change_password = false where login = 'admin';
