package io.pinkspider.global.event;

import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionStatus;
import java.time.LocalDateTime;

/**
 * 미션 상태 변경 이벤트
 * 미션의 상태가 전이될 때 발행되어 히스토리 기록에 사용됨
 */
public record MissionStateChangedEvent(
    String userId,
    Long missionId,
    MissionStatus fromStatus,
    MissionStatus toStatus,
    String triggerEvent,
    String reason,
    LocalDateTime occurredAt
) implements DomainEvent {

    public MissionStateChangedEvent(String userId, Long missionId, MissionStatus fromStatus,
                                     MissionStatus toStatus, String triggerEvent) {
        this(userId, missionId, fromStatus, toStatus, triggerEvent, null, LocalDateTime.now());
    }

    public MissionStateChangedEvent(String userId, Long missionId, MissionStatus fromStatus,
                                     MissionStatus toStatus, String triggerEvent, String reason) {
        this(userId, missionId, fromStatus, toStatus, triggerEvent, reason, LocalDateTime.now());
    }

    /**
     * 미션 생성 이벤트 생성
     */
    public static MissionStateChangedEvent ofCreation(String userId, Long missionId, MissionStatus initialStatus) {
        return new MissionStateChangedEvent(userId, missionId, null, initialStatus, "CREATE");
    }

    /**
     * 미션 오픈 이벤트 생성
     */
    public static MissionStateChangedEvent ofOpen(String userId, Long missionId, MissionStatus fromStatus) {
        return new MissionStateChangedEvent(userId, missionId, fromStatus, MissionStatus.OPEN, "OPEN");
    }

    /**
     * 미션 시작 이벤트 생성
     */
    public static MissionStateChangedEvent ofStart(String userId, Long missionId, MissionStatus fromStatus) {
        return new MissionStateChangedEvent(userId, missionId, fromStatus, MissionStatus.IN_PROGRESS, "START");
    }

    /**
     * 미션 완료 이벤트 생성
     */
    public static MissionStateChangedEvent ofComplete(String userId, Long missionId, MissionStatus fromStatus) {
        return new MissionStateChangedEvent(userId, missionId, fromStatus, MissionStatus.COMPLETED, "COMPLETE");
    }

    /**
     * 미션 취소 이벤트 생성
     */
    public static MissionStateChangedEvent ofCancel(String userId, Long missionId, MissionStatus fromStatus) {
        return new MissionStateChangedEvent(userId, missionId, fromStatus, MissionStatus.CANCELLED, "CANCEL");
    }

    /**
     * 사유가 있는 미션 취소 이벤트 생성
     */
    public static MissionStateChangedEvent ofCancelWithReason(String userId, Long missionId,
                                                               MissionStatus fromStatus, String reason) {
        return new MissionStateChangedEvent(userId, missionId, fromStatus, MissionStatus.CANCELLED, "CANCEL", reason);
    }
}
