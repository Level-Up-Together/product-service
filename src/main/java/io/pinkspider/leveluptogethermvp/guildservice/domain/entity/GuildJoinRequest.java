package io.pinkspider.leveluptogethermvp.guildservice.domain.entity;

import io.pinkspider.global.domain.auditentity.LocalDateTimeBaseEntity;
import io.pinkspider.leveluptogethermvp.guildservice.domain.enums.JoinRequestStatus;
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
import jakarta.validation.constraints.NotNull;
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
@Table(name = "guild_join_request")
@Comment("길드 가입 신청")
public class GuildJoinRequest extends LocalDateTimeBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    @Comment("가입 신청 ID")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "guild_id", nullable = false)
    @Comment("길드")
    private Guild guild;

    @NotNull
    @Column(name = "requester_id", nullable = false)
    @Comment("신청자 ID")
    private String requesterId;

    @Column(name = "message", length = 500)
    @Comment("가입 신청 메시지")
    private String message;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Comment("신청 상태")
    @Builder.Default
    private JoinRequestStatus status = JoinRequestStatus.PENDING;

    @Column(name = "processed_by")
    @Comment("처리자 ID")
    private String processedBy;

    @Column(name = "processed_at")
    @Comment("처리 일시")
    private LocalDateTime processedAt;

    @Column(name = "reject_reason", length = 500)
    @Comment("거절 사유")
    private String rejectReason;

    public boolean isPending() {
        return this.status == JoinRequestStatus.PENDING;
    }

    public void approve(String processedBy) {
        this.status = JoinRequestStatus.APPROVED;
        this.processedBy = processedBy;
        this.processedAt = LocalDateTime.now();
    }

    public void reject(String processedBy, String reason) {
        this.status = JoinRequestStatus.REJECTED;
        this.processedBy = processedBy;
        this.processedAt = LocalDateTime.now();
        this.rejectReason = reason;
    }

    public void cancel() {
        this.status = JoinRequestStatus.CANCELLED;
        this.processedAt = LocalDateTime.now();
    }
}
