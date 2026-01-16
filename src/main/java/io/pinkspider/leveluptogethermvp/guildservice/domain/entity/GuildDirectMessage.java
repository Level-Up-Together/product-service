package io.pinkspider.leveluptogethermvp.guildservice.domain.entity;

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
@Table(name = "guild_direct_message",
    indexes = {
        @Index(name = "idx_dm_conversation", columnList = "conversation_id"),
        @Index(name = "idx_dm_conversation_created", columnList = "conversation_id, created_at DESC"),
        @Index(name = "idx_dm_sender", columnList = "sender_id")
    })
@Comment("길드 1:1 DM 메시지")
public class GuildDirectMessage extends LocalDateTimeBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    @Comment("ID")
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id", nullable = false)
    @Comment("대화")
    private GuildDirectConversation conversation;

    @NotNull
    @Column(name = "sender_id", nullable = false)
    @Comment("발신자 ID")
    private String senderId;

    @Column(name = "sender_nickname", length = 50)
    @Comment("발신자 닉네임")
    private String senderNickname;

    @NotNull
    @Column(name = "content", nullable = false, length = 1000)
    @Comment("메시지 내용")
    private String content;

    @Column(name = "image_url", length = 500)
    @Comment("이미지 URL")
    private String imageUrl;

    @Column(name = "is_read")
    @Comment("읽음 여부")
    @Builder.Default
    private Boolean isRead = false;

    @Column(name = "read_at")
    @Comment("읽은 시간")
    private LocalDateTime readAt;

    @Column(name = "is_deleted")
    @Comment("삭제 여부")
    @Builder.Default
    private Boolean isDeleted = false;

    @Column(name = "deleted_at")
    @Comment("삭제 시간")
    private LocalDateTime deletedAt;

    /**
     * 텍스트 메시지 생성
     */
    public static GuildDirectMessage createTextMessage(
            GuildDirectConversation conversation,
            String senderId,
            String senderNickname,
            String content) {
        return GuildDirectMessage.builder()
            .conversation(conversation)
            .senderId(senderId)
            .senderNickname(senderNickname)
            .content(content)
            .build();
    }

    /**
     * 이미지 메시지 생성
     */
    public static GuildDirectMessage createImageMessage(
            GuildDirectConversation conversation,
            String senderId,
            String senderNickname,
            String content,
            String imageUrl) {
        return GuildDirectMessage.builder()
            .conversation(conversation)
            .senderId(senderId)
            .senderNickname(senderNickname)
            .content(content != null ? content : "")
            .imageUrl(imageUrl)
            .build();
    }

    /**
     * 메시지 읽음 처리
     */
    public void markAsRead() {
        if (!this.isRead) {
            this.isRead = true;
            this.readAt = LocalDateTime.now();
        }
    }

    /**
     * 메시지 삭제 (soft delete)
     */
    public void delete() {
        this.isDeleted = true;
        this.deletedAt = LocalDateTime.now();
        this.content = "[삭제된 메시지입니다]";
    }

    /**
     * 수신자 ID 조회
     */
    public String getRecipientId() {
        return conversation.getOtherUserId(senderId);
    }
}
