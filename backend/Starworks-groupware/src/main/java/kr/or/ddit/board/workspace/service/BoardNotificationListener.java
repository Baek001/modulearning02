package kr.or.ddit.board.workspace.service;

import java.util.Set;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import kr.or.ddit.websocket.service.impl.NotificationServiceImpl;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class BoardNotificationListener {

    private final NotificationServiceImpl notificationService;

    @Async("boardNotificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleBoardNotification(BoardNotificationEvent event) {
        if (event == null) {
            return;
        }

        dispatch(
            event.mentionReceiverIds(),
            event.senderId(),
            notificationService.prepareBoardNotification("BOARD_MENTION", event.pstId(), event.pstTtl())
        );
        dispatch(
            event.urgentReceiverIds(),
            event.senderId(),
            notificationService.prepareBoardNotification("BOARD_URGENT", event.pstId(), event.pstTtl())
        );
    }

    private void dispatch(
        Set<String> receiverIds,
        String senderId,
        NotificationServiceImpl.ResolvedNotification notification
    ) {
        if (notification == null || receiverIds == null || receiverIds.isEmpty()) {
            return;
        }

        for (String receiverId : receiverIds) {
            notificationService.sendResolvedNotification(receiverId, senderId, notification);
        }
    }
}
