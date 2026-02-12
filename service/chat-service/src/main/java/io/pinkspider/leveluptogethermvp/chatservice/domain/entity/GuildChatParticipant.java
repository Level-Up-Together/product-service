package io.pinkspider.leveluptogethermvp.chatservice.domain.entity;

import io.pinkspider.global.domain.auditentity.LocalDateTimeBaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
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

@Entity
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(name = "guild_chat_participant",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_chat_participant_guild_user", columnNames = {"guild_id", "user_id"})
    },
    indexes = {
        @Index(name = "idx_chat_participant_guild", columnList = "guild_id"),
        @Index(name = "idx_chat_participant_guild_active", columnList = "guild_id, is_active")
    })
@Comment("길드 채팅방 참여자")
public class GuildChatParticipant extends LocalDateTimeBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    @Comment("ID")
    private Long id;

    @NotNull
    @Column(name = "guild_id", nullable = false)
    @Comment("길드 ID")
    private Long guildId;

    @NotNull
    @Column(name = "user_id", nullable = false)
    @Comment("사용자 ID")
    private String userId;

    @NotNull
    @Column(name = "user_nickname")
    @Comment("사용자 닉네임")
    private String userNickname;

    @NotNull
    @Column(name = "joined_at", nullable = false)
    @Comment("입장 시간")
    private LocalDateTime joinedAt;

    @Column(name = "left_at")
    @Comment("퇴장 시간")
    private LocalDateTime leftAt;

    @NotNull
    @Column(name = "is_active", nullable = false)
    @Comment("활성 상태")
    private Boolean isActive;

    public static GuildChatParticipant create(Long guildId, String userId, String userNickname) {
        return GuildChatParticipant.builder()
            .guildId(guildId)
            .userId(userId)
            .userNickname(userNickname)
            .joinedAt(LocalDateTime.now())
            .isActive(true)
            .build();
    }

    public void rejoin(String userNickname) {
        this.userNickname = userNickname;
        this.joinedAt = LocalDateTime.now();
        this.leftAt = null;
        this.isActive = true;
    }

    public void leave() {
        this.leftAt = LocalDateTime.now();
        this.isActive = false;
    }

    public boolean isParticipating() {
        return this.isActive != null && this.isActive;
    }
}
