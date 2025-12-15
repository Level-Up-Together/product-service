package io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.entity;

import io.pinkspider.global.domain.auditentity.LocalDateTimeBaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
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
@Table(name = "experience_history")
@Comment("경험치 획득 이력")
public class ExperienceHistory extends LocalDateTimeBaseEntity {

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
    @Column(name = "source_type", nullable = false, length = 30)
    @Comment("획득 유형")
    private ExpSourceType sourceType;

    @Column(name = "source_id")
    @Comment("출처 ID (미션 ID 등)")
    private Long sourceId;

    @NotNull
    @Column(name = "exp_amount", nullable = false)
    @Comment("획득 경험치")
    private Integer expAmount;

    @Column(name = "description", length = 200)
    @Comment("설명")
    private String description;

    @Column(name = "category_name", length = 50)
    @Comment("카테고리명 (미션 카테고리)")
    private String categoryName;

    @Column(name = "level_before")
    @Comment("획득 전 레벨")
    private Integer levelBefore;

    @Column(name = "level_after")
    @Comment("획득 후 레벨")
    private Integer levelAfter;

    public enum ExpSourceType {
        MISSION_EXECUTION,      // 미션 수행 완료
        MISSION_FULL_COMPLETION, // 미션 전체 완료 보너스
        ACHIEVEMENT,             // 업적 보상
        BONUS,                   // 보너스
        ADMIN_GRANT,             // 관리자 지급
        EVENT                    // 이벤트
    }
}
