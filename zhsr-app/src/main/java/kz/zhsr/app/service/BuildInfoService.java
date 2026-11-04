package kz.zhsr.app.service;

import kz.zhsr.app.dto.BuildInfo;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.info.GitProperties;
import org.springframework.stereotype.Service;

/**
 * Сведения о сборке приложения: из build-info.properties и git.properties.
 */
@Service
public class BuildInfoService {

    private final BuildInfo buildInfo;

    public BuildInfoService(BuildProperties build, GitProperties git) {
        this.buildInfo = new BuildInfo(build.getArtifact(), build.getVersion(), build.getTime(),
                git.getBranch(), git.getShortCommitId(), git.getCommitTime());
    }

    public BuildInfo get() {
        return buildInfo;
    }

}
