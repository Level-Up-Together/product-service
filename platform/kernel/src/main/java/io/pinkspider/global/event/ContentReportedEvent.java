package io.pinkspider.global.event;

import java.time.LocalDateTime;

/**
 * 콘텐츠 신고 이벤트
 * 신고 접수 완료 후 대상 유저에게 알림을 보내기 위한 이벤트
 */
public record ContentReportedEvent(
    String userId,              // 신고자 ID
    String targetType,          // 신고 대상 타입 (ReportTargetType name)
    String targetId,            // 신고 대상 ID
    String targetUserId,        // 신고 대상 콘텐츠 소유자 ID (nullable)
    String targetTypeDescription, // 신고 대상 타입 설명 (예: "피드", "길드")
    LocalDateTime occurredAt
) implements DomainEvent {

    public ContentReportedEvent(String userId, String targetType, String targetId,
                                String targetUserId, String targetTypeDescription) {
        this(userId, targetType, targetId, targetUserId, targetTypeDescription, LocalDateTime.now());
    }
}
