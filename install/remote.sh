# Общее для скриптов install/remote-* (подключается через «.»): адрес сервера и ключ - из .env, ssh, выкладка
# install/base на сервер. Команды на сервере пользователь выполняет в группе docker, sudo - только в remote-setup.

[ -f .env ] || { echo "Нет .env (образец - .env.sample)" >&2; exit 1; }
set -a; . ./.env; set +a

: "${DEPLOY_HOST:?в .env задайте DEPLOY_HOST: пользователь@адрес сервера}"
: "${DEPLOY_SSH_KEY:?в .env задайте DEPLOY_SSH_KEY: путь к ssh-ключу}"
DEPLOY_DIR=${DEPLOY_DIR:-/opt/zhsr}
case $DEPLOY_HOST in
  *@*) ;;
  *) echo "DEPLOY_HOST - пользователь@адрес: ubuntu@... (AMI Ubuntu) или ec2-user@... (Amazon Linux)" >&2; exit 1 ;;
esac

# ~ в пути к ключу из .env оболочка не раскрывает
DEPLOY_SSH_KEY=$(eval echo "$DEPLOY_SSH_KEY")
[ -f "$DEPLOY_SSH_KEY" ] || { echo "Нет ssh-ключа $DEPLOY_SSH_KEY" >&2; exit 1; }

# локаль машины разработчика ssh передаёт на сервер, а там её может не быть - предупреждения; C.UTF-8 есть везде
SSH="env LC_ALL=C.UTF-8 ssh -i $DEPLOY_SSH_KEY -o StrictHostKeyChecking=accept-new $DEPLOY_HOST"

echo "Сервер: $DEPLOY_HOST, каталог: $DEPLOY_DIR"

# Пакет install/base -> каталог на сервере: каждый раз текущий, чтобы сервер не отставал от образов; .env на сервере
# не трогается
sync_base() {
    echo '--- пакет -> сервер'
    tar -C install/base -cf - . | $SSH "tar -C '$DEPLOY_DIR' -xf -"
}
