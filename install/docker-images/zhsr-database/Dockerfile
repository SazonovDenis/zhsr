# Миграции БД (Liquibase) и пересоздание БД. Без команды - только миграции; reset - пересоздать БД (prepare/db-reset).
# См. run. Данных в образе нет: каталог db-data подключает app-reset.
FROM eclipse-temurin:22-jre-noble

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
