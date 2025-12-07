package io.pinkspider.leveluptogethermvp.guildservice.domain.entity;

import io.pinkspider.global.domain.auditentity.LocalDateTimeBaseEntity;
import io.pinkspider.leveluptogethermvp.guildservice.domain.enums.ChatMessageType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
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
@Table(name = "guild_chat_message",
    indexes = {
        @Index(name = "idx_chat_guild", columnList = "guild_id"),
        @Index(name = "idx_chat_guild_created", columnList = "guild_id, created_at DESC"),
        @Index(name = "idx_chat_sender", columnList = "sender_id")
    })
@Comment("길드 채팅 메시지")
public class GuildChatMessage extends LocalDateTimeBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    @Comment("ID")
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "guild_id", nullable = false)
    @Comment("길드")
    private Guild guild;

    @Column(name = "sender_id")
    @Comment("발신자 ID (시스템 메시지는 null)")
    private String senderId;

    @Column(name = "sender_nickname", length = 50)
    @Comment("발신자 닉네임")
    private String senderNickname;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false, length = 20)
    @Comment("메시지 타입")
    @Builder.Default
    private ChatMessageType messageType = ChatMessageType.TEXT;

    @NotNull
    @Column(name = "content", nullable = false, length = 1000)
    @Comment("메시지 내용")
    private String content;

    @Column(name = "image_url", length = 500)
    @Comment("이미지 URL")
    private String imageUrl;

    @Column(name = "reference_type", length = 30)
    @Comment("참조 타입 (ACHIEVEMENT, MISSION 등)")
    private String referenceType;

    @Column(name = "reference_id")
    @Comment("참조 ID")
    private Long referenceId;

    @Column(name = "is_deleted")
    @Comment("삭제 여부")
    @Builder.Default
    private Boolean isDeleted = false;

    @Column(name = "deleted_at")
    @Comment("삭제 시간")
    private LocalDateTime deletedAt;

    public static GuildChatMessage createTextMessage(Guild guild, String senderId,
                                                      String senderNickname, String content) {
        return GuildChatMessage.builder()
            .guild(guild)
            .senderId(senderId)
            .senderNickname(senderNickname)
            .messageType(ChatMessageType.TEXT)
            .content(content)
            .build();
    }

    public static GuildChatMessage createSystemMessage(Guild guild, ChatMessageType type,
                                                        String content) {
        return GuildChatMessage.builder()
            .guild(guild)
            .messageType(type)
            .content(content)
            .build();
    }

    public static GuildChatMessage createSystemMessage(Guild guild, ChatMessageType type,
                                                        String content, String referenceType,
                                                        Long referenceId) {
        return GuildChatMessage.builder()
            .guild(guild)
            .messageType(type)
            .content(content)
            .referenceType(referenceType)
            .referenceId(referenceId)
            .build();
    }

    public void delete() {
        this.isDeleted = true;
        this.deletedAt = LocalDateTime.now();
        this.content = "[삭제된 메시지입니다]";
    }

    public boolean isSystemMessage() {
        return messageType.isSystemMessage();
    }
}
