package io.pinkspider.leveluptogethermvp.statsservice.firstday.domain.entity;

import io.pinkspider.global.domain.auditentity.LocalDateTimeBaseEntity;
import io.pinkspider.leveluptogethermvp.statsservice.firstday.domain.enums.FirstDayGroup;
import io.pinkspider.leveluptogethermvp.statsservice.firstday.domain.enums.SignupPlatform;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;

/**
 * LUT-779: 유저 1명의 첫날 행동 그룹 + D1·D7·D30 재방문. 그룹은 첫날(24시간)이 끝난 뒤 한 번 정하고, D1·D7·D30 은 그 날(KST 가입일 +
 * N)이 닫히면 채운다 — null 은 "아직 확정 전". 어드민 리텐션 화면(LUT-780)은 이 표만 읽는다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "first_day_retention")
@Comment("첫날 행동별 리텐션 — 유저 1행 (LUT-779)")
public class FirstDayRetention extends LocalDateTimeBaseEntity {

    @Id
    @Column(name = "user_id", nullable = false, length = 255)
    private String userId;

    @Column(name = "signup_at", nullable = false)
    @Comment("가입 시각(UTC)")
    private LocalDateTime signupAt;

    @Column(name = "signup_date", nullable = false)
    @Comment("가입일(KST) — D1·D7·D30 기준일")
    private LocalDate signupDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "platform", nullable = false, length = 10)
    @Comment("가입 플랫폼 IOS·ANDROID·WEB·UNKNOWN")
    private SignupPlatform platform;

    @Column(name = "first_day_completed", nullable = false)
    @Comment("첫날 미션 완료 1회 이상")
    private boolean firstDayCompleted;

    @Column(name = "first_day_interacted", nullable = false)
    @Comment("첫날 다른 사람과 상호작용")
    private boolean firstDayInteracted;

    @Enumerated(EnumType.STRING)
    @Column(name = "first_day_group", nullable = false, length = 20)
    @Comment("NOT_COMPLETED·SOLO·SOCIAL")
    private FirstDayGroup firstDayGroup;

    @Column(name = "d1")
    @Comment("가입일+1(KST)에 접속 — null 이면 아직 그날이 안 지남")
    private Boolean d1;

    @Column(name = "d7")
    private Boolean d7;

    @Column(name = "d30")
    private Boolean d30;

    public static FirstDayRetention classify(
            String userId,
            LocalDateTime signupAt,
            LocalDate signupDate,
            SignupPlatform platform,
            boolean completed,
            boolean interacted) {
        FirstDayRetention row = new FirstDayRetention();
        row.userId = userId;
        row.signupAt = signupAt;
        row.signupDate = signupDate;
        row.platform = platform;
        row.firstDayCompleted = completed;
        row.firstDayInteracted = interacted;
        row.firstDayGroup = FirstDayGroup.of(completed, interacted);
        return row;
    }

    /**
     * 닫힌 날(가입일 + N ≤ lastCompleteDate)의 재방문을 채운다. 이미 채운 값은 그대로 둔다 — 매일 미확정 유저만 다시 본다.
     *
     * @param activeDates 이 유저가 접속한 KST 날짜들
     * @return 이번에 하나라도 채웠는가
     */
    public boolean applyRetention(Set<LocalDate> activeDates, LocalDate lastCompleteDate) {
        boolean changed = false;
        if (d1 == null && isClosed(1, lastCompleteDate)) {
            d1 = activeDates.contains(signupDate.plusDays(1));
            changed = true;
        }
        if (d7 == null && isClosed(7, lastCompleteDate)) {
            d7 = activeDates.contains(signupDate.plusDays(7));
            changed = true;
        }
        if (d30 == null && isClosed(30, lastCompleteDate)) {
            d30 = activeDates.contains(signupDate.plusDays(30));
            changed = true;
        }
        return changed;
    }

    private boolean isClosed(int days, LocalDate lastCompleteDate) {
        return !signupDate.plusDays(days).isAfter(lastCompleteDate);
    }
}
