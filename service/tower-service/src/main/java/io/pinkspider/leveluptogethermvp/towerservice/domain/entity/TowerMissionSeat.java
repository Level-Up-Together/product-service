package io.pinkspider.leveluptogethermvp.towerservice.domain.entity;

import io.pinkspider.global.domain.auditentity.LocalDateTimeBaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;

/**
 * LUT-632: 미션 수행 한 건 동안의 탑 자리 기록 — 앉아 있던 층·방·자동 착석 여부·받은 응원 수. 미션 완료 응답(완료 화면 안내)과 지표(자동 착석 분리)에
 * 쓴다. 점유자 행({@code tower_occupant})은 자리를 비우면 지워지지만 이 기록은 남는다. 응원 수는 LUT-633(리액션)이 올린다.
 */
@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(
        name = "tower_mission_seat",
        indexes = {
            @Index(name = "idx_tower_mission_seat_user_open", columnList = "user_id, ended_at"),
            @Index(
                    name = "idx_tower_mission_seat_execution",
                    columnList = "user_id, execution_kind, execution_id")
        })
@Comment("레벨업 탑 미션별 자리 기록")
public class TowerMissionSeat extends LocalDateTimeBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "mission_id")
    private Long missionId;

    @Column(name = "execution_id", nullable = false)
    @Comment("일반 미션 실행 ID 또는 고정 미션 인스턴스 ID (execution_kind 로 구분)")
    private Long executionId;

    @Column(name = "execution_kind", nullable = false, length = 10)
    @Comment("REGULAR / PINNED")
    private String executionKind;

    @Column(name = "floor_number", nullable = false)
    private Integer floorNumber;

    @Column(name = "instance_id", nullable = false)
    private Long instanceId;

    @Column(name = "auto_seated", nullable = false)
    @Comment("자동 착석으로 시작한 자리인가 (지표 분리용, 화면에는 내리지 않음)")
    @Builder.Default
    private Boolean autoSeated = false;

    @Column(name = "cheer_count", nullable = false)
    @Builder.Default
    private Integer cheerCount = 0;

    @Column(name = "seated_at", nullable = false)
    private LocalDateTime seatedAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    /** LUT-693: 미션 완료로 끝났을 때의 시각 — 실제 흔적("오늘 이 층에서 N명이 완료") 집계용 */
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    /** LUT-693: 완료한 자리 (배경 원본 픽셀, 모르면 null) — 누구인지는 내리지 않는다 */
    @Column(name = "seat_x")
    private Integer seatX;

    @Column(name = "seat_y")
    private Integer seatY;

    public static TowerMissionSeat open(
            String userId,
            Long missionId,
            Long executionId,
            String executionKind,
            int floorNumber,
            Long instanceId,
            boolean autoSeated,
            LocalDateTime now) {
        return TowerMissionSeat.builder()
                .userId(userId)
                .missionId(missionId)
                .executionId(executionId)
                .executionKind(executionKind)
                .floorNumber(floorNumber)
                .instanceId(instanceId)
                .autoSeated(autoSeated)
                .cheerCount(0)
                .seatedAt(now)
                .build();
    }

    public boolean isAutoSeated() {
        return Boolean.TRUE.equals(autoSeated);
    }

    public boolean isOpen() {
        return endedAt == null;
    }

    public void close(LocalDateTime now) {
        if (endedAt == null) {
            endedAt = now;
        }
    }

    /** LUT-693: 미션 완료로 닫는다 — 완료 시각과 그때의 자리(모르면 null) */
    public void complete(LocalDateTime now, Integer x, Integer y) {
        close(now);
        if (completedAt == null) {
            completedAt = now;
            seatX = x;
            seatY = y;
        }
    }
}
