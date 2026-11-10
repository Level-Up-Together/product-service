package io.pinkspider.leveluptogethermvp.towerservice.domain.entity;

import io.pinkspider.global.domain.auditentity.LocalDateTimeBaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;

/**
 * LUT-634: 층 첫 입장 기록 — 유저×층 1행. insert 는 {@code ON CONFLICT DO NOTHING} 네이티브 쿼리로만 하고(두 번째 입장은 0행),
 * 처음 들어간 순간에 업적·칭호 재판정 이벤트를 낸다.
 */
@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(
        name = "tower_floor_visit",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_tower_floor_visit",
                        columnNames = {"user_id", "floor_number"}))
@Comment("레벨업 탑 층 첫 입장 기록")
public class TowerFloorVisit extends LocalDateTimeBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "floor_number", nullable = false)
    private Integer floorNumber;

    @Column(name = "first_entered_at", nullable = false)
    private LocalDateTime firstEnteredAt;
}
