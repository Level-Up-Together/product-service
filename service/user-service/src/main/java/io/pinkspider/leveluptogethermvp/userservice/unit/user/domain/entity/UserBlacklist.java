package io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.entity;

import io.pinkspider.global.domain.auditentity.LocalDateTimeBaseEntity;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.enums.BlacklistType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "user_blacklist")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class UserBlacklist extends LocalDateTimeBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "blacklist_type", nullable = false, length = 20)
    private BlacklistType blacklistType;

    @Column(name = "reason", nullable = false, length = 500)
    private String reason;

    @Column(name = "admin_id")
    private Long adminId;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @lombok.Builder.Default
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    public void deactivate() {
        this.isActive = false;
    }
}
