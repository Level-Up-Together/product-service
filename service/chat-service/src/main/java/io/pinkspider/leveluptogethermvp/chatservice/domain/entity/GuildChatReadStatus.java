package io.pinkspider.leveluptogethermvp.chatservice.domain.entity;

import io.pinkspider.global.domain.auditentity.LocalDateTimeBaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
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

@Entity
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(name = "guild_chat_read_status",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_read_status_guild_user", columnNames = {"guild_id", "user_id"})
    },
    indexes = {
        @Index(name = "idx_read_status_guild", columnList = "guild_id"),
        @Index(name = "idx_read_status_guild_message", columnList = "guild_id, last_read_message_id")
    })
@Comment("길드 채팅 읽음 상태")
public class GuildChatReadStatus extends LocalDateTimeBaseEntity {

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "last_read_message_id")
    @Comment("마지막으로 읽은 메시지 ID")
    private GuildChatMessage lastReadMessage;

    @Column(name = "last_read_at")
    @Comment("마지막 읽은 시간")
    private LocalDateTime lastReadAt;

    public static GuildChatReadStatus create(Long guildId, String userId) {
        return GuildChatReadStatus.builder()
            .guildId(guildId)
            .userId(userId)
            .lastReadAt(LocalDateTime.now())
            .build();
    }

    public void updateLastRead(GuildChatMessage message) {
        if (this.lastReadMessage != null && this.lastReadMessage.getId() >= message.getId()) {
            return;
        }
        this.lastReadMessage = message;
        this.lastReadAt = LocalDateTime.now();
    }

    public Long getLastReadMessageId() {
        return lastReadMessage != null ? lastReadMessage.getId() : 0L;
    }
}
