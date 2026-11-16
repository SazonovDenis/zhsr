#!/bin/sh
# Сертификат сайта: если на хосте положен свой (server.crt, server.key в смонтированном /etc/nginx/ssl-host - его
# кладёт install/base/ssl-letsencrypt), берём его, иначе остаётся самоподписанный из образа. Запускается образом nginx
# перед стартом.
if [ -f /etc/nginx/ssl-host/server.crt ] && [ -f /etc/nginx/ssl-host/server.key ]; then
    cp /etc/nginx/ssl-host/server.crt /etc/nginx/ssl-host/server.key /etc/nginx/ssl/
    echo "ssl: сертификат с хоста"
else
    echo "ssl: самоподписанный сертификат"
fi
