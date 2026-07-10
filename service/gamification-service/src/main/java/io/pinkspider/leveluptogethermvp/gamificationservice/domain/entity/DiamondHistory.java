package io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity;

import io.pinkspider.global.domain.auditentity.LocalDateTimeBaseEntity;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.enums.DiamondType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Comment;

/**
 * QA-220: 다이아 획득/사용 이력 (원장).
 * balance_after 로 시점별 잔액을 기록해 가장 최근 행이 현재 보유 다이아와 일치한다.
 */
@Entity
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(name = "diamond_history",
    indexes = {
        @Index(name = "idx_diamond_history_user", columnList = "user_id, id"),
        @Index(name = "idx_diamond_history_user_type_source", columnList = "user_id, type, source_id")
    })
@Comment("다이아 획득/사용 이력")
public class DiamondHistory extends LocalDateTimeBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    @Comment("ID")
    private Long id;

    @NotNull
    @Column(name = "user_id", nullable = false)
    @Comment("사용자 ID")
    private String userId;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    @Comment("유형 (LEVEL_UP/MISSION_BOOK/SHOP)")
    private DiamondType type;

    @Column(name = "source_id")
    @Comment("출처 ID (미션북 템플릿 ID, 상점 아이템 ID 등)")
    private Long sourceId;

    @NotNull
    @Column(name = "amount", nullable = false)
    @Comment("증감량 (획득 +, 차감 -)")
    private Integer amount;

    @NotNull
    @Column(name = "balance_after", nullable = false)
    @Comment("적용 후 잔액")
    private Integer balanceAfter;

    @Column(name = "description", length = 500)
    @Comment("설명")
    private String description;
}
