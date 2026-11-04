package io.pinkspider.leveluptogethermvp.towerservice.domain.entity;

import io.pinkspider.global.domain.auditentity.LocalDateTimeBaseEntity;
import io.pinkspider.leveluptogethermvp.towerservice.domain.enums.TowerVisitorSanctionTarget;
import io.pinkspider.leveluptogethermvp.towerservice.domain.enums.TowerVisitorSanctionType;
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
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;

/** LUT-705: 방문객 제재(기기·IP 단위). 해제는 행을 지우지 않고 {@code lifted_at} 을 남긴다(이력) */
@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(name = "tower_visitor_sanction")
@Comment("레벨업 탑 방문객 제재 (기기·IP 단위)")
public class TowerVisitorSanction extends LocalDateTimeBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 10)
    private TowerVisitorSanctionTarget targetType;

    @Column(name = "target_value", nullable = false, length = 128)
    private String targetValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "sanction_type", nullable = false, length = 10)
    private TowerVisitorSanctionType sanctionType;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "lifted_at")
    private LocalDateTime liftedAt;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    public boolean isActive(LocalDateTime now) {
        return liftedAt == null && (expiresAt == null || expiresAt.isAfter(now));
    }

    /** 이 제재가 그 IP·기기의 {@code wanted} 를 막는가 */
    public boolean blocks(
            TowerVisitorSanctionType wanted, String ip, String deviceId, LocalDateTime now) {
        if (!isActive(now) || !sanctionType.blocks(wanted)) {
            return false;
        }
        String value = targetType == TowerVisitorSanctionTarget.IP ? ip : deviceId;
        return value != null && value.equals(targetValue);
    }

    public void lift(LocalDateTime now) {
        if (liftedAt == null) {
            liftedAt = now;
        }
    }
}
