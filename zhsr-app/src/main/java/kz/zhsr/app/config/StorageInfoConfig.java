package kz.zhsr.app.config;

import kz.zhsr.app.service.storage.*;
import org.slf4j.*;
import org.springframework.boot.context.event.*;
import org.springframework.context.*;
import org.springframework.context.annotation.*;

/**
 * Вывод сведений о сборке и подключениях в лог при старте. Сведения о сборке - из build-info.properties
 * (springBoot.buildInfo()) и git.properties (плагин gradle-git-properties); подключения - БД (spring.datasource) и
 * хранилище файлов ({@link FileStorage}).
 */
@Configuration
public class StorageInfoConfig {

    private static final Logger log = LoggerFactory.getLogger("build");

    @Bean
    public ApplicationListener<ApplicationReadyEvent> storageInfoPrinter(
            FileStorage storage
    ) {
        return event -> {
            log.info("=========================");
            log.info("StorageInfoConfig:");
            log.info("  endpoint: {}", storage.endpoint());
            log.info("  bucket:   {}", storage.bucket());
            log.info("");
        };
    }

}
