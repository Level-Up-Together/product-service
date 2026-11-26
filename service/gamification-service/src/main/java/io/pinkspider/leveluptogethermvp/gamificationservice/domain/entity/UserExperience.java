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

    /** LUT-860: 미션 취소로 생긴 누적 마이너스 경험치. 다음 미션 경험치를 줄 때 먼저 갚는다. */
    @Builder.Default
    @Column(name = "exp_debt", nullable = false)
    @Comment("누적 마이너스 경험치")
    private Integer expDebt = 0;

    @Version
    @Column(name = "version")
    @Comment("낙관적 락 버전")
    private Long version;

    /** 경험치 추가 (어뷰징 방지 검증 포함) - 음수 경험치 방지 - Integer overflow 방지 */
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
            log.warn(
                    "경험치가 최대값으로 조정됩니다: userId={}, requestedExp={}, actualExp={}",
                    this.userId,
                    exp,
                    actualExp);
        } else {
            this.currentExp += exp;
            this.totalExp += exp;
        }
    }

    /**
     * LUT-860: 취소한 미션의 경험치를 뺀다. 레벨은 내리지 않는다 — 현재 레벨 안의 경험치에서 빼고, 모자라면 0 까지만 뺀 뒤 남는 양을 누적 마이너스 경험치로
     * 쌓는다.
     *
     * @return 누적 마이너스 경험치로 넘어간 양
     */
    public int cancelExperience(int exp) {
        if (exp < 0) {
            throw new IllegalArgumentException("경험치는 음수일 수 없습니다: " + exp);
        }
        int removable = Math.min(this.currentExp, exp);
        this.currentExp -= removable;
        this.totalExp = Math.max(0, this.totalExp - removable);
        int toDebt = exp - removable;
        addDebt(toDebt);
        return toDebt;
    }

    /**
     * LUT-860: 받을 경험치로 누적 마이너스 경험치를 먼저 갚는다.
     *
     * @return 갚은 양 (이만큼 덜 받는다)
     */
    public int payDebt(int grant) {
        int debt = this.expDebt == null ? 0 : this.expDebt;
        int paid = Math.max(0, Math.min(debt, grant));
        this.expDebt = debt - paid;
        return paid;
    }

    /** LUT-860: 누적 마이너스 경험치를 늘린다 (취소로 못 뺀 양, 지급을 되돌릴 때 갚았던 양) */
    public void addDebt(int amount) {
        if (amount <= 0) {
            return;
        }
        this.expDebt = (this.expDebt == null ? 0 : this.expDebt) + amount;
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
