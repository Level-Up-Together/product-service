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
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "guild_id", nullable = false)
    @Comment("길드")
    private Guild guild;

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

    /**
     * 대화 생성 (user ID 정렬하여 저장)
     */
    public static GuildDirectConversation create(Guild guild, String userIdA, String userIdB) {
        // user ID를 정렬하여 저장 (중복 방지)
        String userId1 = userIdA.compareTo(userIdB) < 0 ? userIdA : userIdB;
        String userId2 = userIdA.compareTo(userIdB) < 0 ? userIdB : userIdA;

        return GuildDirectConversation.builder()
            .guild(guild)
            .userId1(userId1)
            .userId2(userId2)
            .isActive(true)
            .build();
    }

    /**
     * 마지막 메시지 정보 업데이트
     */
    public void updateLastMessage(String content) {
        this.lastMessageAt = LocalDateTime.now();
        this.lastMessageContent = content.length() > 100 ? content.substring(0, 100) : content;
    }

    /**
     * 대화 상대방 ID 조회
     */
    public String getOtherUserId(String currentUserId) {
        return userId1.equals(currentUserId) ? userId2 : userId1;
    }

    /**
     * 특정 사용자가 대화 참여자인지 확인
     */
    public boolean isParticipant(String userId) {
        return userId1.equals(userId) || userId2.equals(userId);
    }

    /**
     * 대화 비활성화
     */
    public void deactivate() {
        this.isActive = false;
    }
}
