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
import java.time.LocalDate;
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
@Table(name = "attendance_record",
    uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "attendance_date"}),
    indexes = {
        @Index(name = "idx_attendance_user_date", columnList = "user_id, attendance_date"),
        @Index(name = "idx_attendance_user_month", columnList = "user_id, year_month")
    })
@Comment("출석 기록")
public class AttendanceRecord extends LocalDateTimeBaseEntity {

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
    @Column(name = "attendance_date", nullable = false)
    @Comment("출석 날짜")
    private LocalDate attendanceDate;

    @Column(name = "year_month", nullable = false, length = 7)
    @Comment("년월 (YYYY-MM)")
    private String yearMonth;

    @Column(name = "day_of_month", nullable = false)
    @Comment("일")
    private Integer dayOfMonth;

    @Column(name = "consecutive_days")
    @Comment("연속 출석 일수")
    @Builder.Default
    private Integer consecutiveDays = 1;

    @Column(name = "reward_exp")
    @Comment("획득 경험치")
    @Builder.Default
    private Integer rewardExp = 0;

    @Column(name = "bonus_reward_exp")
    @Comment("보너스 경험치 (연속 출석 등)")
    @Builder.Default
    private Integer bonusRewardExp = 0;

    @Version
    @Column(name = "version")
    @Comment("낙관적 락 버전")
    private Long version;

    public static AttendanceRecord create(String userId, LocalDate date, int consecutiveDays) {
        return AttendanceRecord.builder()
            .userId(userId)
            .attendanceDate(date)
            .yearMonth(date.getYear() + "-" + String.format("%02d", date.getMonthValue()))
            .dayOfMonth(date.getDayOfMonth())
            .consecutiveDays(consecutiveDays)
            .build();
    }

    public int getTotalRewardExp() {
        return rewardExp + bonusRewardExp;
    }
}
