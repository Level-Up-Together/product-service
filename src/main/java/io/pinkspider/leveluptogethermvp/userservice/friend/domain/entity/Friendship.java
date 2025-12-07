package io.pinkspider.leveluptogethermvp.userservice.friend.domain.entity;

import io.pinkspider.global.domain.auditentity.LocalDateTimeBaseEntity;
import io.pinkspider.leveluptogethermvp.userservice.friend.domain.enums.FriendshipStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "friendship",
    uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "friend_id"}),
    indexes = {
        @Index(name = "idx_friendship_user", columnList = "user_id"),
        @Index(name = "idx_friendship_friend", columnList = "friend_id"),
        @Index(name = "idx_friendship_status", columnList = "status")
    })
@Comment("친구 관계")
public class Friendship extends LocalDateTimeBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    @Comment("ID")
    private Long id;

    @NotNull
    @Column(name = "user_id", nullable = false)
    @Comment("사용자 ID (요청자)")
    private String userId;

    @NotNull
    @Column(name = "friend_id", nullable = false)
    @Comment("친구 ID (수신자)")
    private String friendId;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Comment("상태")
    @Builder.Default
    private FriendshipStatus status = FriendshipStatus.PENDING;

    @Column(name = "requested_at")
    @Comment("요청 시간")
    private LocalDateTime requestedAt;

    @Column(name = "accepted_at")
    @Comment("수락 시간")
    private LocalDateTime acceptedAt;

    @Column(name = "blocked_at")
    @Comment("차단 시간")
    private LocalDateTime blockedAt;

    @Column(name = "message", length = 200)
    @Comment("친구 요청 메시지")
    private String message;

    public static Friendship createRequest(String userId, String friendId, String message) {
        return Friendship.builder()
            .userId(userId)
            .friendId(friendId)
            .status(FriendshipStatus.PENDING)
            .requestedAt(LocalDateTime.now())
            .message(message)
            .build();
    }

    public void accept() {
        if (this.status != FriendshipStatus.PENDING) {
            throw new IllegalStateException("대기 중인 요청만 수락할 수 있습니다.");
        }
        this.status = FriendshipStatus.ACCEPTED;
        this.acceptedAt = LocalDateTime.now();
    }

    public void reject() {
        if (this.status != FriendshipStatus.PENDING) {
            throw new IllegalStateException("대기 중인 요청만 거절할 수 있습니다.");
        }
        this.status = FriendshipStatus.REJECTED;
    }

    public void block() {
        this.status = FriendshipStatus.BLOCKED;
        this.blockedAt = LocalDateTime.now();
    }

    public boolean isPending() {
        return this.status == FriendshipStatus.PENDING;
    }

    public boolean isAccepted() {
        return this.status == FriendshipStatus.ACCEPTED;
    }

    public boolean isBlocked() {
        return this.status == FriendshipStatus.BLOCKED;
    }
}
