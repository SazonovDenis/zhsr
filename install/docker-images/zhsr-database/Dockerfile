# Два образа (buildx --target):
#   zhsr-database      - миграции БД (Liquibase) и пересоздание БД с данными db-data/default. Без команды - только
#                        миграции; reset - пересоздать БД (prepare/db-reset). См. run.
#   zhsr-database-demo - то же и демо-данные db-data/demo: reset --demo - ещё демо-данные и фото демо-заявок.
FROM eclipse-temurin:22-jre-noble AS zhsr-database

ENV DEBIAN_FRONTEND=noninteractive

# psql - загрузка данных; чистка apt - в том же RUN: отдельной инструкцией она места в образе не освобождает
RUN apt-get update \
    && apt-get install -y --no-install-recommends postgresql-client \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

# клиент S3 (mc) - из образа хранилища: статический, под ту же архитектуру
COPY --from=pgsty/silo:latest /usr/bin/mcli /usr/local/bin/mc

WORKDIR /app
COPY ./tmp/app/ /app/
COPY --chmod=755 ./run /app/run

# миграции без Gradle - для prepare/db-reset
ENV DB_MIGRATE="java -Xmx256m -jar /app/zhsr-database-0.0.1.jar"

ENTRYPOINT ["/app/run"]
CMD ["migrate"]

# демо-данные - только в этом образе
FROM zhsr-database AS zhsr-database-demo
COPY ./tmp/demo /app/db-data/demo
