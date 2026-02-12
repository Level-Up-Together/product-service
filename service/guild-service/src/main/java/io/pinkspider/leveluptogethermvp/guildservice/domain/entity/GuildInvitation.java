package io.pinkspider.leveluptogethermvp.guildservice.domain.entity;

import io.pinkspider.global.domain.auditentity.LocalDateTimeBaseEntity;
import io.pinkspider.leveluptogethermvp.guildservice.domain.enums.GuildInvitationStatus;
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
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Comment;

/**
 * 길드 초대 엔티티
 * 비공개 길드에서 마스터가 다른 유저를 초대할 때 사용
 */
@Entity
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(name = "guild_invitation")
@Comment("길드 초대")
public class GuildInvitation extends LocalDateTimeBaseEntity {

    private static final int DEFAULT_EXPIRATION_DAYS = 7;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    @Comment("초대 ID")
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "guild_id", nullable = false)
    @Comment("길드")
    private Guild guild;

    @NotNull
    @Column(name = "inviter_id", nullable = false)
    @Comment("초대한 유저 ID (마스터)")
    private String inviterId;

    @NotNull
    @Column(name = "invitee_id", nullable = false)
    @Comment("초대 받은 유저 ID")
    private String inviteeId;

    @Size(max = 500)
    @Column(name = "message", length = 500)
    @Comment("초대 메시지")
    private String message;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Comment("초대 상태")
    private GuildInvitationStatus status;

    @NotNull
    @Column(name = "expires_at", nullable = false)
    @Comment("만료 시간")
    private LocalDateTime expiresAt;

    @Column(name = "processed_at")
    @Comment("처리 시간 (수락/거절/취소)")
    private LocalDateTime processedAt;

    /**
     * 초대 생성
     */
    public static GuildInvitation create(Guild guild, String inviterId, String inviteeId, String message) {
        return GuildInvitation.builder()
            .guild(guild)
            .inviterId(inviterId)
            .inviteeId(inviteeId)
            .message(message)
            .status(GuildInvitationStatus.PENDING)
            .expiresAt(LocalDateTime.now().plusDays(DEFAULT_EXPIRATION_DAYS))
            .build();
    }

    /**
     * 초대 수락
     */
    public void accept() {
        this.status = GuildInvitationStatus.ACCEPTED;
        this.processedAt = LocalDateTime.now();
    }

    /**
     * 초대 거절
     */
    public void reject() {
        this.status = GuildInvitationStatus.REJECTED;
        this.processedAt = LocalDateTime.now();
    }

    /**
     * 초대 취소 (마스터가 취소)
     */
    public void cancel() {
        this.status = GuildInvitationStatus.CANCELLED;
        this.processedAt = LocalDateTime.now();
    }

    /**
     * 초대 만료 처리
     */
    public void expire() {
        this.status = GuildInvitationStatus.EXPIRED;
        this.processedAt = LocalDateTime.now();
    }

    /**
     * 대기 중인지 확인
     */
    public boolean isPending() {
        return this.status == GuildInvitationStatus.PENDING;
    }

    /**
     * 만료되었는지 확인
     */
    public boolean isExpired() {
        return this.expiresAt.isBefore(LocalDateTime.now());
    }
}
