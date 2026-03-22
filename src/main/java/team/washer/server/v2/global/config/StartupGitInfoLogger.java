package team.washer.server.v2.global.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.info.GitProperties;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class StartupGitInfoLogger {

    private static final String GIT_COMMIT_MESSAGE_SHORT_KEY = "commit.message.short";

    private final ObjectProvider<GitProperties> gitPropertiesProvider;
    private final ObjectProvider<BuildProperties> buildPropertiesProvider;

    @EventListener(ApplicationReadyEvent.class)
    public void logStartupInfo() {
        BuildProperties buildProperties = buildPropertiesProvider.getIfAvailable();
        if (buildProperties != null) {
            log.info("Build info: version={}", buildProperties.getVersion());
        }

        GitProperties gitProperties = gitPropertiesProvider.getIfAvailable();
        if (gitProperties == null) {
            return;
        }

        log.info("Git info: branch={}, commitId={}, shortCommitId={}, commitMessage={}, commitTime={}",
                gitProperties.getBranch(),
                gitProperties.getCommitId(),
                gitProperties.getShortCommitId(),
                gitProperties.get(GIT_COMMIT_MESSAGE_SHORT_KEY),
                gitProperties.getCommitTime());
    }
}
