package kz.zhsr.app.dto;

import java.time.Instant;

/**
 * Сведения о сборке приложения: из build-info.properties и git.properties.
 */
public record BuildInfo(String appName, String version, Instant buildDate,
                        String branch, String commitId, Instant commitDate) {
}
