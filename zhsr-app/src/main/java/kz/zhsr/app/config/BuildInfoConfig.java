package kz.zhsr.app.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.info.GitProperties;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Вывод сведений о сборке в лог при старте. Сведения - из build-info.properties (springBoot.buildInfo())
 * и git.properties (плагин gradle-git-properties).
 */
@Configuration
public class BuildInfoConfig {

    private static final Logger log = LoggerFactory.getLogger("build");

    @Bean
    public ApplicationListener<ApplicationReadyEvent> buildInfoPrinter(BuildProperties build, GitProperties git) {
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
