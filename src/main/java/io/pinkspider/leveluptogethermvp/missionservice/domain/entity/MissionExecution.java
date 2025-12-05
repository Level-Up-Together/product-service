package io.pinkspider.leveluptogethermvp.missionservice.domain.entity;

import io.pinkspider.global.domain.auditentity.LocalDateTimeBaseEntity;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.ExecutionStatus;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Comment;

@Entity
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(name = "mission_execution",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_execution_participant_date",
        columnNames = {"participant_id", "execution_date"}
    )
)
@Comment("미션 수행 기록")
public class MissionExecution extends LocalDateTimeBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    @Comment("수행 기록 ID")
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "participant_id", nullable = false)
    @Comment("참여자")
    private MissionParticipant participant;

    @NotNull
    @Column(name = "execution_date", nullable = false)
    @Comment("수행 예정 일자")
    private LocalDate executionDate;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Comment("수행 상태")
    @Builder.Default
    private ExecutionStatus status = ExecutionStatus.PENDING;

    @Column(name = "completed_at")
    @Comment("완료 일시")
    private LocalDateTime completedAt;

    @Column(name = "exp_earned")
    @Comment("획득한 경험치")
    @Builder.Default
    private Integer expEarned = 0;

    @Column(name = "note", length = 500)
    @Comment("메모")
    private String note;

    public void complete() {
        if (this.status == ExecutionStatus.COMPLETED) {
            throw new IllegalStateException("이미 완료된 수행 기록입니다.");
        }
        if (this.status == ExecutionStatus.MISSED) {
            throw new IllegalStateException("미실행 처리된 수행 기록은 완료할 수 없습니다.");
        }
        this.status = ExecutionStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
    }

    public void markAsMissed() {
        if (this.status == ExecutionStatus.COMPLETED) {
            throw new IllegalStateException("완료된 수행 기록은 미실행 처리할 수 없습니다.");
        }
        this.status = ExecutionStatus.MISSED;
    }

    public void setExpEarned(int exp) {
        this.expEarned = exp;
    }
}
