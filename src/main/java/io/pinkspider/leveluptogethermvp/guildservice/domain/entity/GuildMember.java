package io.pinkspider.leveluptogethermvp.guildservice.domain.entity;

import io.pinkspider.global.domain.auditentity.LocalDateTimeBaseEntity;
import io.pinkspider.leveluptogethermvp.guildservice.domain.enums.GuildMemberRole;
import io.pinkspider.leveluptogethermvp.guildservice.domain.enums.GuildMemberStatus;
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
@Table(name = "guild_member", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"guild_id", "user_id"})
})
@Comment("길드 멤버")
public class GuildMember extends LocalDateTimeBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    @Comment("길드 멤버 ID")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "guild_id", nullable = false)
    @Comment("길드")
    private Guild guild;

    @NotNull
    @Column(name = "user_id", nullable = false)
    @Comment("사용자 ID")
    private String userId;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    @Comment("역할")
    @Builder.Default
    private GuildMemberRole role = GuildMemberRole.MEMBER;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Comment("상태")
    @Builder.Default
    private GuildMemberStatus status = GuildMemberStatus.ACTIVE;

    @Column(name = "joined_at")
    @Comment("가입 일시")
    private LocalDateTime joinedAt;

    @Column(name = "left_at")
    @Comment("탈퇴 일시")
    private LocalDateTime leftAt;

    public boolean isMaster() {
        return this.role == GuildMemberRole.MASTER;
    }

    public boolean isAdmin() {
        return this.role == GuildMemberRole.ADMIN;
    }

    public boolean isAdminOrMaster() {
        return this.role == GuildMemberRole.MASTER || this.role == GuildMemberRole.ADMIN;
    }

    public boolean isActive() {
        return this.status == GuildMemberStatus.ACTIVE;
    }

    public void promoteToMaster() {
        this.role = GuildMemberRole.MASTER;
    }

    public void promoteToAdmin() {
        this.role = GuildMemberRole.ADMIN;
    }

    public void demoteToMember() {
        this.role = GuildMemberRole.MEMBER;
    }

    public void leave() {
        this.status = GuildMemberStatus.LEFT;
        this.leftAt = LocalDateTime.now();
    }

    public void kick() {
        this.status = GuildMemberStatus.KICKED;
        this.leftAt = LocalDateTime.now();
    }
}
