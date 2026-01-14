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
import jakarta.persistence.Version;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.annotations.Comment;

/**
 * 사용자별 카테고리 경험치
 * 각 카테고리별로 누적된 경험치를 저장합니다.
 */
@Entity
@Getter
@Setter
@SuperBuilder
@Slf4j
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(name = "user_category_experience",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_user_category_exp",
        columnNames = {"user_id", "category_id"}
    ),
    indexes = {
        @Index(name = "idx_user_cat_exp_user", columnList = "user_id"),
        @Index(name = "idx_user_cat_exp_category", columnList = "category_id"),
        @Index(name = "idx_user_cat_exp_total", columnList = "category_id, total_exp DESC")
    }
)
@Comment("사용자별 카테고리 경험치")
public class UserCategoryExperience extends LocalDateTimeBaseEntity {

    // 경험치 최대값 (Integer overflow 방지)
    private static final int MAX_EXPERIENCE = 999_999_999;

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
    @Column(name = "category_id", nullable = false)
    @Comment("카테고리 ID")
    private Long categoryId;

    @Column(name = "category_name", length = 50)
    @Comment("카테고리명 (비정규화)")
    private String categoryName;

    @Builder.Default
    @Column(name = "total_exp", nullable = false)
    @Comment("카테고리 누적 경험치")
    private Long totalExp = 0L;

    @Version
    @Column(name = "version")
    @Comment("낙관적 락 버전")
    private Long version;

    /**
     * 경험치 추가
     */
    public void addExperience(int exp) {
        if (exp < 0) {
            throw new IllegalArgumentException("경험치는 음수일 수 없습니다: " + exp);
        }
        if (exp == 0) {
            return;
        }

        // Overflow 방지
        if (this.totalExp >= MAX_EXPERIENCE) {
            log.warn("카테고리 경험치가 최대값에 도달했습니다: userId={}, categoryId={}, totalExp={}",
                this.userId, this.categoryId, this.totalExp);
            return;
        }

        // 최대값 초과 방지
        long newTotalExp = this.totalExp + exp;
        if (newTotalExp > MAX_EXPERIENCE) {
            this.totalExp = (long) MAX_EXPERIENCE;
            log.warn("카테고리 경험치가 최대값으로 조정됩니다: userId={}, categoryId={}",
                this.userId, this.categoryId);
        } else {
            this.totalExp = newTotalExp;
        }
    }

    /**
     * 새 UserCategoryExperience 생성
     */
    public static UserCategoryExperience create(String userId, Long categoryId, String categoryName, int initialExp) {
        return UserCategoryExperience.builder()
            .userId(userId)
            .categoryId(categoryId)
            .categoryName(categoryName)
            .totalExp((long) Math.max(0, initialExp))
            .build();
    }
}
