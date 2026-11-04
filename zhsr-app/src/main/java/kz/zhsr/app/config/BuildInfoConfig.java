package kz.zhsr.app.config;

import kz.zhsr.app.service.storage.*;
import org.slf4j.*;
import org.springframework.boot.context.event.*;
import org.springframework.boot.info.*;
import org.springframework.context.*;
import org.springframework.context.annotation.*;

/**
 * Вывод сведений о сборке и подключениях в лог при старте. Сведения о сборке - из build-info.properties
 * (springBoot.buildInfo()) и git.properties (плагин gradle-git-properties); подключения - БД (spring.datasource) и
 * хранилище файлов ({@link FileStorage}).
 */
@Configuration
public class BuildInfoConfig {

    private static final Logger log = LoggerFactory.getLogger("build");

    @Bean
    public ApplicationListener<ApplicationReadyEvent> buildInfoPrinter(
            BuildProperties build, GitProperties git
    ) {
        return event -> {
            log.info("=========================");
            log.info("Application: {}/{}", build.getArtifact(), git.getBranch());
            log.info("  version:     {}", build.getVersion());
            log.info("  build date:  {}", build.getTime());
            log.info("  commit date: {}", git.getCommitTime());
            log.info("  commit id:   {}", git.getShortCommitId());
            log.info("");
        };
    }

}
