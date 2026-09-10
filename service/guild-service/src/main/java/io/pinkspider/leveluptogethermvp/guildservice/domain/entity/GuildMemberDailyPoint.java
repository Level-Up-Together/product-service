package io.pinkspider.leveluptogethermvp.guildservice.domain.entity;

import io.pinkspider.global.domain.auditentity.LocalDateTimeBaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;

/**
 * LUT-483: 길드원 일간 활동 포인트 적립 이력 (유저×일자 멱등 근거).
 *
 * <p>길드 미션으로 획득한 EXP 를 일자(KST) 단위로 누적하고, 사다리(10 EXP = 1점, 상한 60 = 6점)를
 * 누적값에 적용한 점수를 보관한다. 점수 상승분(차분)만 길드 totalPoint 에 더해지므로
 * 쪼개서 수행해도(19→13→30) 몰아서 수행한 것과 같은 점수가 된다.
 */
@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(name = "guild_member_daily_point",
    uniqueConstraints = @UniqueConstraint(name = "uk_guild_member_daily_point",
        columnNames = {"guild_id", "user_id", "point_date"}))
@Comment("길드원 일간 활동 포인트")
public class GuildMemberDailyPoint extends LocalDateTimeBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    @Comment("ID")
    private Long id;

    @NotNull
    @Column(name = "guild_id", nullable = false)
    @Comment("길드 ID")
    private Long guildId;

    @NotNull
    @Column(name = "user_id", nullable = false)
    @Comment("길드원 사용자 ID")
    private String userId;

    @NotNull
    @Column(name = "point_date", nullable = false)
    @Comment("적립 일자 (KST 기준)")
    private LocalDate pointDate;

    @NotNull
    @Column(name = "daily_exp", nullable = false)
    @Comment("그날 길드미션으로 획득한 EXP 누적 (상한 미적용 원값)")
    @Builder.Default
    private Integer dailyExp = 0;

    @NotNull
    @Column(name = "points", nullable = false)
    @Comment("사다리 적용 점수 (0..상한/단위)")
    @Builder.Default
    private Integer points = 0;

    /** EXP 누적 후 사다리 재계산 — 직전 점수와의 차분을 반환한다 (음수 없음) */
    public int accumulate(int expDelta, int unitExp, int dailyCapExp) {
        this.dailyExp += expDelta;
        int newPoints = Math.min(this.dailyExp, dailyCapExp) / unitExp;
        int diff = newPoints - this.points;
        if (diff <= 0) {
            return 0;
        }
        this.points = newPoints;
        return diff;
    }
}
