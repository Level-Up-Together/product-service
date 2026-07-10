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
import org.hibernate.annotations.Comment;

/**
 * QA-220: 사용자 다이아 잔액 (샵 재화)
 *
 * 레벨업 보상은 "보상이 지급된 최고 레벨"(lastRewardedLevel)을 기록해
 * 경험치 환수로 레벨이 내려갔다가 다시 오르는 경우의 중복 지급을 방지한다.
 * 레벨업 보상 총량 상한 999개 = 레벨 1000까지 지급.
 */
@Entity
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(name = "user_diamond")
@Comment("사용자 다이아 잔액")
public class UserDiamond extends LocalDateTimeBaseEntity {

    /** 레벨업 보상 지급 상한 레벨 (레벨업 다이아 최대 999개 = Lv.2 ~ Lv.1000) */
    public static final int MAX_REWARDED_LEVEL = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    @Comment("ID")
    private Long id;

    @NotNull
    @Column(name = "user_id", nullable = false, unique = true)
    @Comment("사용자 ID")
    private String userId;

    @NotNull
    @Column(name = "balance", nullable = false)
    @Comment("현재 보유 다이아")
    @Builder.Default
    private Integer balance = 0;

    @NotNull
    @Column(name = "last_rewarded_level", nullable = false)
    @Comment("레벨업 보상이 지급된 최고 레벨 (중복 지급 방지)")
    @Builder.Default
    private Integer lastRewardedLevel = 1;

    @Version
    @Column(name = "version")
    @Comment("낙관적 락 버전")
    private Long version;

    public static UserDiamond create(String userId) {
        return UserDiamond.builder()
            .userId(userId)
            .balance(0)
            .lastRewardedLevel(1)
            .build();
    }

    /** 다이아 증감 적용 후 잔액 반환. 잔액 부족 시 예외. */
    public int apply(int amount) {
        int newBalance = this.balance + amount;
        if (newBalance < 0) {
            throw new IllegalStateException("다이아 잔액이 부족합니다. balance=" + this.balance + ", amount=" + amount);
        }
        this.balance = newBalance;
        return newBalance;
    }
}
