package io.pinkspider.leveluptogethermvp.missionservice.domain.entity;

import io.pinkspider.global.domain.auditentity.LocalDateTimeBaseEntity;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.IdempotencyStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;

/**
 * LUT-577: 미션 생성 멱등 키. {@code (user_id, idempotency_key)} 유니크로 같은 시도의 동시 요청을 하나만 통과시키고, 완료된 키는 응답
 * 본문을 보관해 재요청에 그대로 돌려준다. 24시간 뒤 배치가 지운다 (만료 후 같은 키는 새 요청).
 */
@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(
        name = "mission_idempotency_key",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_mission_idempotency_user_key",
                        columnNames = {"user_id", "idempotency_key"}),
        indexes = @Index(name = "idx_mission_idempotency_created_at", columnList = "created_at"))
@Comment("미션 생성 멱등 키 (LUT-577)")
public class MissionIdempotencyKey extends LocalDateTimeBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "user_id", nullable = false, length = 36)
    @Comment("요청 유저 ID (키 스코프)")
    private String userId;

    @Column(name = "idempotency_key", nullable = false, length = 36)
    @Comment("클라이언트가 보낸 Idempotency-Key (UUID, 소문자 정규화)")
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Comment("PROCESSING(선점·생성 중) / COMPLETED(생성 완료)")
    private IdempotencyStatus status;

    @Column(name = "mission_id")
    @Comment("생성된 미션 ID")
    private Long missionId;

    @Column(name = "response_body", columnDefinition = "TEXT")
    @Comment("생성 응답(MissionResponse) JSON — 재요청에 그대로 반환")
    private String responseBody;

    @Column(name = "completed_at")
    @Comment("생성 완료 시각")
    private LocalDateTime completedAt;

    /** 키 선점 행 (PROCESSING). 유니크 충돌이면 같은 유저의 같은 키가 이미 있다. */
    public static MissionIdempotencyKey claim(String userId, String idempotencyKey) {
        return MissionIdempotencyKey.builder()
                .userId(userId)
                .idempotencyKey(idempotencyKey)
                .status(IdempotencyStatus.PROCESSING)
                .build();
    }

    public void complete(Long missionId, String responseBody) {
        this.status = IdempotencyStatus.COMPLETED;
        this.missionId = missionId;
        this.responseBody = responseBody;
        this.completedAt = LocalDateTime.now();
    }

    public boolean isCompleted() {
        return status == IdempotencyStatus.COMPLETED;
    }

    /** created_at 기준 경과 판정. 감사 컬럼이 아직 없으면(미flush) 경과하지 않은 것으로 본다. */
    public boolean isOlderThan(Duration duration, LocalDateTime now) {
        LocalDateTime createdAt = getCreatedAt();
        return createdAt != null && createdAt.plus(duration).isBefore(now);
    }
}
