# Ключи частей приложения для install/build-app, install/build-app-docker, install/build-app-docker-push и
# install/remote-update (подключается через «.»): app, database, web - и только они. Без ключей - все части.
#   app      - сервер: образ zhsr-app
#   database - миграции БД: образы zhsr-database и zhsr-database-demo (демо-данные)
#   web      - фронт и nginx: образ zhsr-nginx

ALL_KEYS="database app web"
KEYS=${*:-$ALL_KEYS}

for key in $KEYS; do
    case " $ALL_KEYS " in
      *" $key "*) ;;
      *) echo "неизвестный ключ: $key (app, database, web)" >&2; exit 1 ;;
    esac
done

# образ и сервис на сервере по ключу
image_of() {
    case $1 in
      app) echo zhsr-app ;;
      database) echo zhsr-database ;;
      web) echo zhsr-nginx ;;
    esac
}
