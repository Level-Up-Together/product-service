package io.pinkspider.leveluptogethermvp.missionservice.domain.entity;

import io.pinkspider.global.domain.auditentity.LocalDateTimeBaseEntity;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.ParticipantStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Comment;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(name = "mission_participant",
    uniqueConstraints = @UniqueConstraint(columnNames = {"mission_id", "user_id"}))
@Comment("미션 참여자")
public class MissionParticipant extends LocalDateTimeBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    @Comment("참여자 ID")
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "mission_id", nullable = false)
    @Comment("미션 ID")
    private Mission mission;

    @NotNull
    @Column(name = "user_id", nullable = false)
    @Comment("사용자 ID")
    private String userId;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Comment("참여 상태")
    private ParticipantStatus status;

    @Column(name = "progress")
    @Comment("진행률 (0-100)")
    private Integer progress;

    @Column(name = "joined_at")
    @Comment("참여 일시")
    private LocalDateTime joinedAt;

    @Column(name = "completed_at")
    @Comment("완료 일시")
    private LocalDateTime completedAt;

    @Column(name = "note", length = 500)
    @Comment("메모")
    private String note;

    public void accept() {
        if (this.status != ParticipantStatus.PENDING) {
            throw new IllegalStateException("대기중 상태의 참여자만 승인할 수 있습니다.");
        }
        this.status = ParticipantStatus.ACCEPTED;
    }

    public void startProgress() {
        if (this.status != ParticipantStatus.ACCEPTED) {
            throw new IllegalStateException("승인된 참여자만 진행 시작할 수 있습니다.");
        }
        this.status = ParticipantStatus.IN_PROGRESS;
        this.progress = 0;
    }

    public void updateProgress(int progressValue) {
        // ACCEPTED 또는 IN_PROGRESS 상태에서 진행률 업데이트 가능
        if (this.status != ParticipantStatus.ACCEPTED && this.status != ParticipantStatus.IN_PROGRESS) {
            throw new IllegalStateException("활성 상태에서만 진행률을 업데이트할 수 있습니다.");
        }
        if (progressValue < 0 || progressValue > 100) {
            throw new IllegalArgumentException("진행률은 0-100 사이여야 합니다.");
        }
        // ACCEPTED 상태에서 첫 진행률 업데이트 시 IN_PROGRESS로 전환
        if (this.status == ParticipantStatus.ACCEPTED) {
            this.status = ParticipantStatus.IN_PROGRESS;
        }
        this.progress = progressValue;
    }

    public void complete() {
        if (this.status != ParticipantStatus.IN_PROGRESS) {
            throw new IllegalStateException("진행중 상태의 참여자만 완료할 수 있습니다.");
        }
        this.status = ParticipantStatus.COMPLETED;
        this.progress = 100;
        this.completedAt = LocalDateTime.now();
    }

    public void fail() {
        if (this.status != ParticipantStatus.IN_PROGRESS) {
            throw new IllegalStateException("진행중 상태의 참여자만 실패 처리할 수 있습니다.");
        }
        this.status = ParticipantStatus.FAILED;
    }

    public void withdraw() {
        if (this.status == ParticipantStatus.COMPLETED) {
            throw new IllegalStateException("완료된 미션에서는 철회할 수 없습니다.");
        }
        this.status = ParticipantStatus.WITHDRAWN;
    }

    public void rejoin(ParticipantStatus newStatus) {
        if (this.status != ParticipantStatus.WITHDRAWN && this.status != ParticipantStatus.FAILED) {
            throw new IllegalStateException("탈퇴 또는 실패 상태에서만 재참여할 수 있습니다.");
        }
        this.status = newStatus;
        this.progress = 0;
        this.joinedAt = LocalDateTime.now();
        this.completedAt = null;
    }
}
