package kr.or.ddit.board.workspace.service;

import java.util.Set;

public record BoardNotificationEvent(
    String senderId,
    String pstId,
    String pstTtl,
    Set<String> mentionReceiverIds,
    Set<String> urgentReceiverIds
) {

    public BoardNotificationEvent {
        mentionReceiverIds = mentionReceiverIds == null ? Set.of() : Set.copyOf(mentionReceiverIds);
        urgentReceiverIds = urgentReceiverIds == null ? Set.of() : Set.copyOf(urgentReceiverIds);
    }
}
