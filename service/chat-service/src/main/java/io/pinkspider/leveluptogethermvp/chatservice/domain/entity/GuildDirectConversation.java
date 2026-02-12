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
@Table(name = "guild_direct_conversation",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_dm_conversation", columnNames = {"guild_id", "user_id_1", "user_id_2"})
    },
    indexes = {
        @Index(name = "idx_dm_conv_guild", columnList = "guild_id"),
        @Index(name = "idx_dm_conv_user1", columnList = "user_id_1"),
        @Index(name = "idx_dm_conv_user2", columnList = "user_id_2"),
        @Index(name = "idx_dm_conv_last_message", columnList = "last_message_at DESC")
    })
@Comment("길드 1:1 DM 대화")
public class GuildDirectConversation extends LocalDateTimeBaseEntity {

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
    @Column(name = "user_id_1", nullable = false)
    @Comment("사용자 1 ID (항상 user_id_2보다 작음)")
    private String userId1;

    @NotNull
    @Column(name = "user_id_2", nullable = false)
    @Comment("사용자 2 ID (항상 user_id_1보다 큼)")
    private String userId2;

    @Column(name = "last_message_at")
    @Comment("마지막 메시지 시간")
    private LocalDateTime lastMessageAt;

    @Column(name = "last_message_content", length = 100)
    @Comment("마지막 메시지 미리보기")
    private String lastMessageContent;

    @Column(name = "is_active")
    @Comment("활성 상태")
    @Builder.Default
    private Boolean isActive = true;

    public static GuildDirectConversation create(Long guildId, String userIdA, String userIdB) {
        String userId1 = userIdA.compareTo(userIdB) < 0 ? userIdA : userIdB;
        String userId2 = userIdA.compareTo(userIdB) < 0 ? userIdB : userIdA;

        return GuildDirectConversation.builder()
            .guildId(guildId)
            .userId1(userId1)
            .userId2(userId2)
            .isActive(true)
            .build();
    }

    public void updateLastMessage(String content) {
        this.lastMessageAt = LocalDateTime.now();
        this.lastMessageContent = content.length() > 100 ? content.substring(0, 100) : content;
    }

    public String getOtherUserId(String currentUserId) {
        return userId1.equals(currentUserId) ? userId2 : userId1;
    }

    public boolean isParticipant(String userId) {
        return userId1.equals(userId) || userId2.equals(userId);
    }

    public void deactivate() {
        this.isActive = false;
    }
}
