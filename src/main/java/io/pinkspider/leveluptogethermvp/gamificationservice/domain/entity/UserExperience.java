package io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity;

import io.pinkspider.global.domain.auditentity.LocalDateTimeBaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
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

@Entity
@Getter
@Setter
@SuperBuilder
@Slf4j
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(name = "user_experience")
@Comment("사용자 경험치")
public class UserExperience extends LocalDateTimeBaseEntity {

    // 경험치 최대값 (Integer overflow 방지)
    private static final int MAX_EXPERIENCE = 999_999_999;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    @Comment("ID")
    private Long id;

    @NotNull
    @Column(name = "user_id", nullable = false, unique = true)
    @Comment("사용자 ID")
    private String userId;

    @Builder.Default
    @Column(name = "current_level")
    @Comment("현재 레벨")
    private Integer currentLevel = 1;

    @Builder.Default
    @Column(name = "current_exp")
    @Comment("현재 경험치")
    private Integer currentExp = 0;

    @Builder.Default
    @Column(name = "total_exp")
    @Comment("누적 총 경험치")
    private Integer totalExp = 0;

    @Version
    @Column(name = "version")
    @Comment("낙관적 락 버전")
    private Long version;

    /**
     * 경험치 추가 (어뷰징 방지 검증 포함)
     * - 음수 경험치 방지
     * - Integer overflow 방지
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
            log.warn("경험치가 최대값에 도달했습니다: userId={}, totalExp={}", this.userId, this.totalExp);
            return;
        }

        // 최대값 초과 방지
        long newTotalExp = (long) this.totalExp + exp;
        if (newTotalExp > MAX_EXPERIENCE) {
            int actualExp = MAX_EXPERIENCE - this.totalExp;
            this.currentExp += actualExp;
            this.totalExp = MAX_EXPERIENCE;
            log.warn("경험치가 최대값으로 조정됩니다: userId={}, requestedExp={}, actualExp={}",
                this.userId, exp, actualExp);
        } else {
            this.currentExp += exp;
            this.totalExp += exp;
        }
    }

    public void levelUp(int requiredExpForNextLevel) {
        if (this.currentExp >= requiredExpForNextLevel) {
            this.currentExp -= requiredExpForNextLevel;
            this.currentLevel += 1;
        }
    }

    public void setLevel(int level, int remainingExp) {
        this.currentLevel = level;
        this.currentExp = remainingExp;
    }
}
