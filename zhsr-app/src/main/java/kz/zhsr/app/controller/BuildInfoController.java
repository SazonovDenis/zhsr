package kz.zhsr.app.controller;

import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.info.GitProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * Сведения о сборке приложения для клиента.
 */
@RestController
@RequestMapping("/buildInfo")
public class BuildInfoController {

    public record BuildInfo(String appName, String version, Instant buildDate,
                            String branch, String commitId, Instant commitDate) {
    }

    private final BuildInfo buildInfo;

    public BuildInfoController(BuildProperties build, GitProperties git) {
        this.buildInfo = new BuildInfo(build.getArtifact(), build.getVersion(), build.getTime(),
                git.getBranch(), git.getShortCommitId(), git.getCommitTime());
    }

    @GetMapping
    public BuildInfo get() {
        return buildInfo;
    }

}
