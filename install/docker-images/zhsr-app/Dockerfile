# Сервер zhsr-app (Spring Boot) на JRE 22: библиотеки lib/kis-* собраны под Java 22. Параметры - .env, смонтированный
# в /app; память JVM - JAVA_OPTS в docker-compose.yml
FROM eclipse-temurin:22-jre-noble

WORKDIR /app
COPY ./tmp/zhsr-app-0.0.1.jar /app/

CMD ["sh", "-c", "exec java $JAVA_OPTS -jar zhsr-app-0.0.1.jar"]

EXPOSE 8080
