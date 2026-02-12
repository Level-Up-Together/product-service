package io.pinkspider.leveluptogethermvp.missionservice.domain.entity;

import io.pinkspider.global.enums.MissionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

/**
 * 미션 상태 전이 히스토리
 * 미션의 상태 변경 이력을 추적하기 위한 엔티티
 */
@Entity
@Table(name = "mission_state_history", indexes = {
    @Index(name = "idx_mission_state_history_mission_id", columnList = "mission_id"),
    @Index(name = "idx_mission_state_history_occurred_at", columnList = "occurred_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MissionStateHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "mission_id", nullable = false)
    private Long missionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 20)
    private MissionStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 20)
    private MissionStatus toStatus;

    @Column(name = "trigger_event", nullable = false, length = 30)
    private String triggerEvent;

    @Column(name = "triggered_by", nullable = false, length = 100)
    private String triggeredBy;

    @Column(name = "reason", length = 500)
    private String reason;

    @CreationTimestamp
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private LocalDateTime occurredAt;

    @Builder
    public MissionStateHistory(Long missionId, MissionStatus fromStatus, MissionStatus toStatus,
                               String triggerEvent, String triggeredBy, String reason) {
        this.missionId = missionId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.triggerEvent = triggerEvent;
        this.triggeredBy = triggeredBy;
        this.reason = reason;
    }

    /**
     * 미션 생성 시 히스토리 생성
     */
    public static MissionStateHistory ofCreation(Long missionId, MissionStatus initialStatus, String creatorId) {
        return MissionStateHistory.builder()
            .missionId(missionId)
            .fromStatus(null)
            .toStatus(initialStatus)
            .triggerEvent("CREATE")
            .triggeredBy(creatorId)
            .build();
    }

    /**
     * 상태 전이 히스토리 생성
     */
    public static MissionStateHistory ofTransition(Long missionId, MissionStatus fromStatus,
                                                    MissionStatus toStatus, String event, String userId) {
        return MissionStateHistory.builder()
            .missionId(missionId)
            .fromStatus(fromStatus)
            .toStatus(toStatus)
            .triggerEvent(event)
            .triggeredBy(userId)
            .build();
    }

    /**
     * 사유가 있는 상태 전이 히스토리 생성
     */
    public static MissionStateHistory ofTransitionWithReason(Long missionId, MissionStatus fromStatus,
                                                              MissionStatus toStatus, String event,
                                                              String userId, String reason) {
        return MissionStateHistory.builder()
            .missionId(missionId)
            .fromStatus(fromStatus)
            .toStatus(toStatus)
            .triggerEvent(event)
            .triggeredBy(userId)
            .reason(reason)
            .build();
    }
}
