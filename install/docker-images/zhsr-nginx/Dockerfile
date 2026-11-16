# nginx zhsr: статика zhsr-web (PWA), прокси /api и WebSocket /api/ws на zhsr-app, HTTPS
FROM nginx:alpine

RUN rm /etc/nginx/nginx.conf && \
    rm -f /etc/nginx/conf.d/default.conf

# Самоподписанный сертификат - пока на хосте нет своего (см. 10-ssl.sh, install/base/ssl-letsencrypt)
RUN apk add --no-cache openssl && \
    mkdir -p /etc/nginx/ssl /var/www/acme && \
    openssl req -x509 -nodes -days 3650 -newkey rsa:2048 \
      -keyout /etc/nginx/ssl/server.key \
      -out /etc/nginx/ssl/server.crt \
      -subj "/CN=zhsr"

COPY ./nginx.conf /etc/nginx/nginx.conf
COPY ./zhsr.locations.conf /etc/nginx/zhsr.locations.conf
COPY --chmod=755 ./10-ssl.sh /docker-entrypoint.d/10-ssl.sh

# Статика фронта (build/web - сборка PWA)
COPY ./tmp/web /usr/share/nginx/html

EXPOSE 80 443
