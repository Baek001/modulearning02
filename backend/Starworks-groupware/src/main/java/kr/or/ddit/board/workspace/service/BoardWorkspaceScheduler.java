package kr.or.ddit.board.workspace.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.jobs.board-workspace", name = "enabled", havingValue = "true", matchIfMissing = true)
public class BoardWorkspaceScheduler {

    private final BoardWorkspaceService boardWorkspaceService;

    @Scheduled(cron = "0 * * * * *")
    public void publishDueScheduledPosts() {
        boardWorkspaceService.publishDueScheduledPosts();
    }
}
