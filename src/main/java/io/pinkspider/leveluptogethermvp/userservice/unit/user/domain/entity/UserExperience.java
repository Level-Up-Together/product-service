package io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.entity;

import io.pinkspider.global.domain.auditentity.LocalDateTimeBaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
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
@Table(name = "user_experience")
@Comment("사용자 경험치")
public class UserExperience extends LocalDateTimeBaseEntity {

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

    public void addExperience(int exp) {
        this.currentExp += exp;
        this.totalExp += exp;
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
