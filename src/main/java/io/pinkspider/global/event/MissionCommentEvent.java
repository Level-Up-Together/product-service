package io.pinkspider.global.event;

import java.time.LocalDateTime;

/**
 * 미션 댓글 작성 이벤트
 */
public record MissionCommentEvent(
    String userId,              // 댓글 작성자
    String missionCreatorId,    // 미션 생성자 (알림 받을 사람)
    String commenterNickname,
    Long missionId,
    String missionTitle,
    LocalDateTime occurredAt
) implements DomainEvent {

    public MissionCommentEvent(String userId, String missionCreatorId, String commenterNickname,
                               Long missionId, String missionTitle) {
        this(userId, missionCreatorId, commenterNickname, missionId, missionTitle, LocalDateTime.now());
    }
}
