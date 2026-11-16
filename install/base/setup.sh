#!/bin/sh
set -e

# Подготовка сервера (Ubuntu), один раз: Docker, swap 2 ГБ, .env со случайными паролями.
# Запускать: sudo ./setup.sh (так делает ./remote-setup с машины разработчика). Повторный запуск ничего не ломает.

cd "$(dirname "$0")"

if ! command -v docker > /dev/null; then
    apt-get update
    apt-get install -y docker.io docker-compose-v2
    systemctl enable --now docker
    # журналы контейнеров не должны съесть диск
    mkdir -p /etc/docker
    [ -f /etc/docker/daemon.json ] || echo '{"log-driver": "json-file", "log-opts": {"max-size": "20m", "max-file": "5"}}' \
        > /etc/docker/daemon.json
    systemctl restart docker
fi

# пользователь, запустивший sudo, - в группу docker: команды docker без sudo
if [ -n "$SUDO_USER" ]; then
    usermod -aG docker "$SUDO_USER"
fi

# swap 2 ГБ: на 2 ГБ памяти без него JVM и PostgreSQL при пиках падают
if ! swapon --show | grep -q /swapfile; then
    fallocate -l 2G /swapfile
    chmod 600 /swapfile
    mkswap /swapfile
    swapon /swapfile
    grep -q '^/swapfile' /etc/fstab || echo '/swapfile none swap sw 0 0' >> /etc/fstab
fi

# .env - из env.sample со случайными паролями; уже есть - не трогаем
if [ ! -f .env ]; then
    cp env.sample .env
    for _ in 1 2 3; do
        sed -i "0,/CHANGE_ME/s//$(od -An -N12 -tx1 /dev/urandom | tr -d ' \n')/" .env
    done
    chmod 600 .env
    [ -n "$SUDO_USER" ] && chown "$SUDO_USER" .env
    echo ".env создан"
fi

mkdir -p ssl acme
[ -n "$SUDO_USER" ] && chown "$SUDO_USER" ssl acme
echo "Сервер готов"
