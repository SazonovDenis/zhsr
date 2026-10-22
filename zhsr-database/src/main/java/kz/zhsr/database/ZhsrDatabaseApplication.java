package kz.zhsr.database;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Приводит структуру БД к актуальной: при старте Spring Boot применяет миграции Liquibase и завершается.
 */
@SpringBootApplication
public class ZhsrDatabaseApplication {
    public static void main(String[] args) {
        SpringApplication.run(ZhsrDatabaseApplication.class, args);
    }
}
