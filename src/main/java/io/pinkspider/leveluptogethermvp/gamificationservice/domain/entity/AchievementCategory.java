package io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity;

import io.pinkspider.global.domain.auditentity.LocalDateTimeBaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Comment;

/**
 * 업적 카테고리
 * 업적의 분류를 정의하며, 관리자가 동적으로 추가/삭제할 수 있습니다.
 */
@Entity
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(name = "achievement_category",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_achievement_category_code",
        columnNames = {"code"}
    ),
    indexes = {
        @Index(name = "idx_achievement_category_sort", columnList = "sort_order"),
        @Index(name = "idx_achievement_category_active", columnList = "is_active")
    }
)
@Comment("업적 카테고리")
public class AchievementCategory extends LocalDateTimeBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    @Comment("카테고리 ID")
    private Long id;

    @NotNull
    @Column(name = "code", nullable = false, length = 30)
    @Comment("카테고리 코드 (MISSION, GUILD, SOCIAL, LEVEL, STREAK, SPECIAL 등)")
    private String code;

    @NotNull
    @Column(name = "name", nullable = false, length = 50)
    @Comment("카테고리 이름")
    private String name;

    @Column(name = "description", length = 200)
    @Comment("카테고리 설명")
    private String description;

    @NotNull
    @Column(name = "sort_order", nullable = false)
    @Comment("정렬 순서")
    @Builder.Default
    private Integer sortOrder = 0;

    @NotNull
    @Column(name = "is_active", nullable = false)
    @Comment("활성 여부")
    @Builder.Default
    private Boolean isActive = true;
}
