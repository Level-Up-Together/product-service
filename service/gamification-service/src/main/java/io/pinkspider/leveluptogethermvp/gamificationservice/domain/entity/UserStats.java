package io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity;

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
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
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
@Table(
        name = "user_stats",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_user_stats_user_id",
                        columnNames = {"user_id"}))
@Comment("유저 통계")
public class UserStats extends LocalDateTimeBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    @Comment("ID")
    private Long id;

    @NotNull
    @Column(name = "user_id", nullable = false)
    @Comment("유저 ID")
    private String userId;

    // 미션 관련 통계
    @Column(name = "total_mission_completions", nullable = false)
    @Comment("총 미션 수행 완료 횟수")
    @Builder.Default
    private Integer totalMissionCompletions = 0;

    @Column(name = "total_mission_full_completions", nullable = false)
    @Comment("총 미션 전체 완료 횟수")
    @Builder.Default
    private Integer totalMissionFullCompletions = 0;

    @Column(name = "total_guild_mission_completions", nullable = false)
    @Comment("총 길드 미션 수행 완료 횟수")
    @Builder.Default
    private Integer totalGuildMissionCompletions = 0;

    // 연속 활동 통계
    @Column(name = "current_streak", nullable = false)
    @Comment("현재 연속 활동 일수")
    @Builder.Default
    private Integer currentStreak = 0;

    @Column(name = "max_streak", nullable = false)
    @Comment("최대 연속 활동 일수")
    @Builder.Default
    private Integer maxStreak = 0;

    @Column(name = "last_activity_date")
    @Comment("마지막 활동 날짜")
    private LocalDate lastActivityDate;

    // LUT-717 연속 실천 (하루 미션 1개 이상 완료, 유저 로컬 날짜·완료 시각 기준) — 출석이 섞인 current_streak 과 별도
    @Column(name = "practice_streak", nullable = false)
    @Comment("현재 연속 실천 일수")
    @Builder.Default
    private Integer practiceStreak = 0;

    @Column(name = "max_practice_streak", nullable = false)
    @Comment("최장 연속 실천 일수")
    @Builder.Default
    private Integer maxPracticeStreak = 0;

    @Column(name = "last_practice_date")
    @Comment("마지막 실천 날짜 (유저 로컬) — NULL 이면 첫 조회 때 이력으로 재계산")
    private LocalDate lastPracticeDate;

    // LUT-723 구독자 연속 실천 방어권 — 매달 2개(이월 없음), 연속으로 놓친 날 최대 2일
    @Column(name = "practice_shield_month", length = 7)
    @Comment("방어권 사용을 센 달 (유저 로컬 YYYY-MM) — 지금 달이 아니면 사용 0")
    private String practiceShieldMonth;

    @Column(name = "practice_shields_used", nullable = false)
    @Comment("practice_shield_month 에 쓴 방어권 수")
    @Builder.Default
    private Integer practiceShieldsUsed = 0;

    @Column(name = "last_practice_shield_date")
    @Comment("마지막으로 방어된 날 (유저 로컬)")
    private LocalDate lastPracticeShieldDate;

    // 업적/칭호 통계
    @Column(name = "total_achievements_completed", nullable = false)
    @Comment("완료한 업적 수")
    @Builder.Default
    private Integer totalAchievementsCompleted = 0;

    @Column(name = "total_titles_acquired", nullable = false)
    @Comment("획득한 칭호 수")
    @Builder.Default
    private Integer totalTitlesAcquired = 0;

    // 랭킹용 포인트
    @Column(name = "ranking_points", nullable = false)
    @Comment("랭킹 포인트")
    @Builder.Default
    private Long rankingPoints = 0L;

    // 미션 완주 관련 통계
    @Column(name = "max_completed_mission_duration", nullable = false)
    @Comment("완주한 미션 중 최대 기간(일)")
    @Builder.Default
    private Integer maxCompletedMissionDuration = 0;

    // 소셜 통계 (크로스-서비스 카운터)
    @Column(name = "total_likes_received", nullable = false)
    @Comment("받은 총 좋아요 수")
    @Builder.Default
    private Long totalLikesReceived = 0L;

    @Column(name = "friend_count", nullable = false)
    @Comment("친구 수")
    @Builder.Default
    private Integer friendCount = 0;

    @Column(name = "guild_join_count", nullable = false)
    @Comment("가입해 본 distinct 길드 수 (LUT-418: 재가입 중복 집계 방지)")
    @Builder.Default
    private Integer guildJoinCount = 0;

    @Column(name = "total_comments_received", nullable = false)
    @Comment("받은 총 댓글 수 (피드+미션)")
    @Builder.Default
    private Long totalCommentsReceived = 0L;

    public void incrementMissionCompletion() {
        this.totalMissionCompletions++;
        updateRankingPoints();
    }

    public void incrementMissionFullCompletion(int durationDays) {
        this.totalMissionFullCompletions++;
        if (durationDays > this.maxCompletedMissionDuration) {
            this.maxCompletedMissionDuration = durationDays;
        }
        updateRankingPoints();
    }

    public void incrementGuildMissionCompletion() {
        this.totalGuildMissionCompletions++;
        updateRankingPoints();
    }

    public void decrementMissionCompletion() {
        if (this.totalMissionCompletions > 0) {
            this.totalMissionCompletions--;
            updateRankingPoints();
        }
    }

    public void decrementMissionFullCompletion() {
        if (this.totalMissionFullCompletions > 0) {
            this.totalMissionFullCompletions--;
            updateRankingPoints();
        }
    }

    public void decrementGuildMissionCompletion() {
        if (this.totalGuildMissionCompletions > 0) {
            this.totalGuildMissionCompletions--;
            updateRankingPoints();
        }
    }

    public void updateStreak(LocalDate today) {
        if (lastActivityDate == null) {
            this.currentStreak = 1;
        } else if (lastActivityDate.equals(today)) {
            // 같은 날 중복 호출 - 아무것도 하지 않음
            return;
        } else if (today.isBefore(lastActivityDate)) {
            // LUT-405: 과거 날짜 유입은 무시한다. 호출자마다 날짜의 시계가 달라
            // (출석=유저 타임존, 미션 완료=서버 UTC) KST 00~09시 미션 완료가 어제 날짜로
            // 들어오던 케이스 — 리셋하면 streak 이 파괴되고 lastActivityDate 까지 역행해
            // 이후 비교도 연쇄적으로 꼬인다.
            return;
        } else if (lastActivityDate.plusDays(1).equals(today)) {
            // 연속
            this.currentStreak++;
        } else {
            // 연속 끊김
            this.currentStreak = 1;
        }

        if (this.currentStreak > this.maxStreak) {
            this.maxStreak = this.currentStreak;
        }
        this.lastActivityDate = today;
        updateRankingPoints();
    }

    /**
     * LUT-717: 미션 완료 1건을 실천 날짜(유저 로컬)로 반영한다. 같은 날 중복과 과거 날짜(지연 도착)는 무시, 바로 다음 날이면 +1, 건너뛰면 1 부터. 랭킹
     * 포인트·활동 스트릭은 건드리지 않는다.
     */
    public void recordPractice(LocalDate practiceDate) {
        recordPractice(practiceDate, day -> false);
    }

    /**
     * LUT-723: 건너뛴 날이 있으면 구독자 방어권으로 이을 수 있는지 본다 — 놓친 날이 연속 최대 2일이고, 그날마다 구독 중이었고({@code
     * entitledOn}), 그 달 방어권이 남아 있으면 방어권을 쓰고 +1(방어된 날은 일수에 더하지 않는다). 아니면 1 부터.
     */
    public void recordPractice(LocalDate practiceDate, Predicate<LocalDate> entitledOn) {
        if (practiceDate == null) {
            return;
        }
        if (lastPracticeDate == null) {
            this.practiceStreak = 1;
        } else if (lastPracticeDate.equals(practiceDate)
                || practiceDate.isBefore(lastPracticeDate)) {
            return;
        } else if (lastPracticeDate.plusDays(1).equals(practiceDate)) {
            this.practiceStreak++;
        } else {
            Optional<ShieldPlan> plan = planShields(practiceDate, entitledOn);
            if (plan.isPresent()) {
                applyShields(plan.get());
                this.practiceStreak++;
            } else {
                this.practiceStreak = 1;
            }
        }
        if (this.practiceStreak > this.maxPracticeStreak) {
            this.maxPracticeStreak = this.practiceStreak;
        }
        this.lastPracticeDate = practiceDate;
    }

    /**
     * LUT-717: 완료 이력 전체(유저 로컬 날짜 집합)로 연속 실천을 처음부터 다시 계산한다 — 배포 전 가입 유저의 첫 조회, 정합 보정용. 빈 집합이면 아무것도
     * 바꾸지 않는다(다음 조회 때 다시 시도).
     */
    public void rebuildPracticeStreak(Collection<LocalDate> practiceDates) {
        if (practiceDates == null || practiceDates.isEmpty()) {
            return;
        }
        List<LocalDate> sorted =
                practiceDates.stream().filter(Objects::nonNull).distinct().sorted().toList();
        int run = 0;
        int best = 0;
        LocalDate prev = null;
        for (LocalDate date : sorted) {
            run = (prev != null && prev.plusDays(1).equals(date)) ? run + 1 : 1;
            best = Math.max(best, run);
            prev = date;
        }
        this.practiceStreak = run;
        this.maxPracticeStreak =
                Math.max(best, this.maxPracticeStreak == null ? 0 : this.maxPracticeStreak);
        this.lastPracticeDate = prev;
    }

    /** LUT-723: 한 달 방어권 수 */
    public static final int MONTHLY_PRACTICE_SHIELDS = 2;

    /** LUT-723: 연속으로 놓친 날 중 방어할 수 있는 최대 일수 — 3일 연속 놓치면 끊긴다 */
    public static final int MAX_SHIELDED_GAP_DAYS = 2;

    /** LUT-723: 놓친 날을 방어권으로 덮는 계획 — 덮을 날, 그 뒤의 달·사용 수 */
    public record ShieldPlan(List<LocalDate> days, String month, int used) {}

    /** LUT-723: 지금 시점의 연속 실천(방어 반영)과 이번 달 남은 방어권 — 조회는 저장하지 않는다(다음 실천 때 실제로 쓴다) */
    public record PracticeView(int currentDays, int shieldsRemaining, LocalDate lastShieldDate) {}

    /**
     * LUT-723: 마지막 실천일과 {@code untilExclusive} 사이(둘 다 제외)의 놓친 날을 방어권으로 덮을 수 있으면 그 계획. 놓친 날이 없으면 빈
     * 계획, 덮을 수 없으면 empty. 방어권은 날마다 그 날의 달에서 쓴다(달이 바뀌면 다시 2개).
     */
    public Optional<ShieldPlan> planShields(
            LocalDate untilExclusive, Predicate<LocalDate> entitledOn) {
        if (lastPracticeDate == null || untilExclusive == null) {
            return Optional.empty();
        }
        long missedCount = ChronoUnit.DAYS.between(lastPracticeDate, untilExclusive) - 1;
        if (missedCount < 0 || missedCount > MAX_SHIELDED_GAP_DAYS) {
            return Optional.empty();
        }
        String month = practiceShieldMonth;
        int used = practiceShieldsUsed == null ? 0 : practiceShieldsUsed;
        List<LocalDate> days = new ArrayList<>();
        for (LocalDate day = lastPracticeDate.plusDays(1);
                day.isBefore(untilExclusive);
                day = day.plusDays(1)) {
            if (!entitledOn.test(day)) {
                return Optional.empty();
            }
            String dayMonth = YearMonth.from(day).toString();
            if (!dayMonth.equals(month)) {
                month = dayMonth;
                used = 0;
            }
            if (used >= MONTHLY_PRACTICE_SHIELDS) {
                return Optional.empty();
            }
            used++;
            days.add(day);
        }
        return Optional.of(new ShieldPlan(List.copyOf(days), month, used));
    }

    private void applyShields(ShieldPlan plan) {
        if (plan.days().isEmpty()) {
            return;
        }
        this.practiceShieldMonth = plan.month();
        this.practiceShieldsUsed = plan.used();
        this.lastPracticeShieldDate = plan.days().get(plan.days().size() - 1);
    }

    /**
     * LUT-723: 오늘(유저 로컬) 기준 연속 실천 — 마지막 실천이 오늘·어제면 그대로, 그 사이 놓친 날(어제까지)을 방어할 수 있으면 유지, 아니면 0. 남은
     * 방어권은 구독 중({@code entitledToday})일 때만 이번 달 기준(방어할 날을 쓴 뒤의 값).
     */
    public PracticeView practiceViewAsOf(
            LocalDate today, Predicate<LocalDate> entitledOn, boolean entitledToday) {
        int current = 0;
        String month = practiceShieldMonth;
        int used = practiceShieldsUsed == null ? 0 : practiceShieldsUsed;
        LocalDate lastShield = lastPracticeShieldDate;
        if (lastPracticeDate != null && today != null) {
            if (!lastPracticeDate.isBefore(today.minusDays(1))) {
                current = practiceStreak;
            } else {
                Optional<ShieldPlan> plan = planShields(today, entitledOn);
                if (plan.isPresent()) {
                    current = practiceStreak;
                    if (!plan.get().days().isEmpty()) {
                        month = plan.get().month();
                        used = plan.get().used();
                        lastShield = plan.get().days().get(plan.get().days().size() - 1);
                    }
                }
            }
        }
        int remaining = 0;
        if (entitledToday && today != null) {
            int usedThisMonth = YearMonth.from(today).toString().equals(month) ? used : 0;
            remaining = Math.max(0, MONTHLY_PRACTICE_SHIELDS - usedThisMonth);
        }
        return new PracticeView(current, remaining, lastShield);
    }

    /** LUT-717: 마지막 실천일이 오늘·어제(유저 로컬)일 때만 현재 연속을 인정한다 — 오늘 아직 안 했으면 어제까지 값, 그 전이면 0. */
    public int currentPracticeStreakAsOf(LocalDate today) {
        if (lastPracticeDate == null || today == null) {
            return 0;
        }
        boolean alive =
                lastPracticeDate.equals(today) || lastPracticeDate.equals(today.minusDays(1));
        return alive ? practiceStreak : 0;
    }

    public void incrementAchievementCompleted() {
        this.totalAchievementsCompleted++;
        updateRankingPoints();
    }

    public void incrementTitleAcquired() {
        this.totalTitlesAcquired++;
    }

    public void incrementLikesReceived() {
        this.totalLikesReceived++;
    }

    public void decrementLikesReceived() {
        if (this.totalLikesReceived > 0) {
            this.totalLikesReceived--;
        }
    }

    public void incrementFriendCount() {
        this.friendCount++;
    }

    public void decrementFriendCount() {
        if (this.friendCount > 0) {
            this.friendCount--;
        }
    }

    public void incrementCommentsReceived() {
        this.totalCommentsReceived++;
    }

    public void decrementCommentsReceived() {
        if (this.totalCommentsReceived > 0) {
            this.totalCommentsReceived--;
        }
    }

    private void updateRankingPoints() {
        // 랭킹 포인트 계산: 미션완료*10 + 전체완료*50 + 길드미션*15 + 연속일수*5 + 업적*100
        this.rankingPoints =
                (long)
                        (totalMissionCompletions * 10
                                + totalMissionFullCompletions * 50
                                + totalGuildMissionCompletions * 15
                                + maxStreak * 5
                                + totalAchievementsCompleted * 100);
    }
}
