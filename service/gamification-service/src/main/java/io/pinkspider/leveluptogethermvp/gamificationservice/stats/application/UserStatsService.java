package io.pinkspider.leveluptogethermvp.gamificationservice.stats.application;

import io.pinkspider.global.facade.GuildQueryFacade;
import io.pinkspider.global.facade.UserQueryFacade;
import io.pinkspider.leveluptogethermvp.gamificationservice.stats.domain.dto.UserStatsResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.UserStats;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.UserStatsRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@Transactional(readOnly = true, transactionManager = "gamificationTransactionManager")
public class UserStatsService {

    private final UserStatsRepository userStatsRepository;
    private final UserQueryFacade userQueryFacade;
    private final GuildQueryFacade guildQueryFacade;

    // LUT-405: userQueryFacadeService → userProfileCacheService → gamificationQueryFacadeService
    // → userStatsService 로 이어지는 user↔gamification 생성 사이클을 @Lazy 로 끊는다.
    // LUT-418: guildQueryFacade 도 같은 이유로 @Lazy (guild↔gamification 생성 사이클 예방).
    public UserStatsService(
            UserStatsRepository userStatsRepository,
            @Lazy UserQueryFacade userQueryFacade,
            @Lazy GuildQueryFacade guildQueryFacade) {
        this.userStatsRepository = userStatsRepository;
        this.userQueryFacade = userQueryFacade;
        this.guildQueryFacade = guildQueryFacade;
    }

    @Transactional(transactionManager = "gamificationTransactionManager")
    public UserStats getOrCreateUserStats(String userId) {
        return userStatsRepository.findByUserId(userId)
            .orElseGet(() -> {
                UserStats newStats = UserStats.builder()
                    .userId(userId)
                    .build();
                return userStatsRepository.save(newStats);
            });
    }

    public UserStatsResponse getUserStats(String userId) {
        UserStats stats = getOrCreateUserStats(userId);
        return UserStatsResponse.from(stats);
    }

    @Transactional(transactionManager = "gamificationTransactionManager")
    public void recordMissionCompletion(String userId, boolean isGuildMission) {
        UserStats stats = getOrCreateUserStats(userId);
        stats.incrementMissionCompletion();
        if (isGuildMission) {
            stats.incrementGuildMissionCompletion();
        }
        // LUT-405: 출석(recordAttendance)과 같은 유저 타임존 날짜를 쓴다.
        // 서버 UTC 날짜(LocalDate.now())를 쓰면 KST 00~09시 미션 완료가 어제 날짜로
        // 들어가 streak 을 리셋시키고 max_streak(연속 출석 업적)이 동결된다.
        stats.updateStreak(LocalDate.now(resolveUserZone(userId)));
        log.debug("미션 완료 기록: userId={}, totalCompletions={}", userId, stats.getTotalMissionCompletions());
    }

    /**
     * 출석 체크 시 호출. user_stats의 currentStreak/maxStreak/lastActivityDate를 갱신한다.
     * QA-113 / B11: 기존에는 attendance_record만 갱신되어 USER_STATS 기반 streak 업적이
     * 트리거되지 않던 문제 해결.
     */
    @Transactional(transactionManager = "gamificationTransactionManager")
    public void recordAttendance(String userId, LocalDate attendanceDate) {
        UserStats stats = getOrCreateUserStats(userId);
        stats.updateStreak(attendanceDate);
        log.debug("출석 streak 갱신: userId={}, currentStreak={}, maxStreak={}",
            userId, stats.getCurrentStreak(), stats.getMaxStreak());
    }

    @Transactional(transactionManager = "gamificationTransactionManager")
    public void undoMissionCompletion(String userId, boolean isGuildMission) {
        UserStats stats = getOrCreateUserStats(userId);
        stats.decrementMissionCompletion();
        if (isGuildMission) {
            stats.decrementGuildMissionCompletion();
        }
        log.debug("미션 완료 보상 처리: userId={}, totalCompletions={}", userId, stats.getTotalMissionCompletions());
    }

    @Transactional(transactionManager = "gamificationTransactionManager")
    public void recordMissionFullCompletion(String userId, int durationDays) {
        UserStats stats = getOrCreateUserStats(userId);
        stats.incrementMissionFullCompletion(durationDays);
        log.debug("미션 전체 완료 기록: userId={}, totalFullCompletions={}, durationDays={}, maxDuration={}",
            userId, stats.getTotalMissionFullCompletions(), durationDays, stats.getMaxCompletedMissionDuration());
    }

    @Transactional(transactionManager = "gamificationTransactionManager")
    public void undoMissionFullCompletion(String userId) {
        UserStats stats = getOrCreateUserStats(userId);
        stats.decrementMissionFullCompletion();
        log.debug("미션 전체 완료 보상 처리: userId={}, totalFullCompletions={}",
            userId, stats.getTotalMissionFullCompletions());
    }

    @Transactional(transactionManager = "gamificationTransactionManager")
    public void recordAchievementCompleted(String userId) {
        UserStats stats = getOrCreateUserStats(userId);
        stats.incrementAchievementCompleted();
    }

    @Transactional(transactionManager = "gamificationTransactionManager")
    public void recordTitleAcquired(String userId) {
        UserStats stats = getOrCreateUserStats(userId);
        stats.incrementTitleAcquired();
    }

    public int getCurrentStreak(String userId) {
        return userStatsRepository.findByUserId(userId)
            .map(UserStats::getCurrentStreak)
            .orElse(0);
    }

    public int getMaxStreak(String userId) {
        return userStatsRepository.findByUserId(userId)
            .map(UserStats::getMaxStreak)
            .orElse(0);
    }

    @Transactional(transactionManager = "gamificationTransactionManager")
    public void incrementLikesReceived(String userId) {
        UserStats stats = getOrCreateUserStats(userId);
        stats.incrementLikesReceived();
    }

    @Transactional(transactionManager = "gamificationTransactionManager")
    public void decrementLikesReceived(String userId) {
        UserStats stats = getOrCreateUserStats(userId);
        stats.decrementLikesReceived();
    }

    /**
     * LUT-418: 길드 가입 카운터를 "가입해 본 distinct 길드 수"로 동기화한다.
     *
     * <p>이벤트마다 +1 하던 기존 방식은 같은 길드 탈퇴→재가입 반복으로 무한 누적됐다. guild_member는 탈퇴/추방/해체/회원탈퇴 모두 소프트
     * 삭제(행 보존)이고 재가입은 기존 행 재활성화라, 전 status 행 수를 그대로 덮어쓰면 재가입·중복 이벤트에 멱등이다. 탈퇴해도 이력 행은 남으므로
     * 카운트가 줄지 않는다 (달성 업적 회수 방지).
     */
    @Transactional(transactionManager = "gamificationTransactionManager")
    public void syncGuildJoinCount(String userId) {
        long distinctGuilds = guildQueryFacade.countDistinctJoinedGuilds(userId);
        UserStats stats = getOrCreateUserStats(userId);
        stats.setGuildJoinCount((int) distinctGuilds);
    }

    @Transactional(transactionManager = "gamificationTransactionManager")
    public void incrementFriendCount(String userId) {
        UserStats stats = getOrCreateUserStats(userId);
        stats.incrementFriendCount();
    }

    @Transactional(transactionManager = "gamificationTransactionManager")
    public void decrementFriendCount(String userId) {
        UserStats stats = getOrCreateUserStats(userId);
        stats.decrementFriendCount();
    }

    @Transactional(transactionManager = "gamificationTransactionManager")
    public void incrementCommentsReceived(String userId) {
        UserStats stats = getOrCreateUserStats(userId);
        stats.incrementCommentsReceived();
    }

    @Transactional(transactionManager = "gamificationTransactionManager")
    public void decrementCommentsReceived(String userId) {
        UserStats stats = getOrCreateUserStats(userId);
        stats.decrementCommentsReceived();
    }

    /**
     * 기존 사용자의 좋아요/친구 카운터 초기화 (일회성 마이그레이션용)
     */
    @Transactional(transactionManager = "gamificationTransactionManager")
    public void syncCountersForUser(String userId, long likesReceived, int friendCount) {
        UserStats stats = getOrCreateUserStats(userId);
        stats.setTotalLikesReceived(likesReceived);
        stats.setFriendCount(friendCount);
    }

    /** LUT-405: streak 날짜 계산은 유저 preferred_timezone 기준 (실패 시 Asia/Seoul 폴백) */
    private ZoneId resolveUserZone(String userId) {
        try {
            String timezone = userQueryFacade.getPreferredTimezone(userId);
            return ZoneId.of(timezone != null ? timezone : "Asia/Seoul");
        } catch (Exception e) {
            return ZoneId.of("Asia/Seoul");
        }
    }

    /**
     * 랭킹 퍼센타일 계산 (상위 X%)
     */
    public Double calculateRankingPercentile(long rankingPoints) {
        long totalUsers = userStatsRepository.countTotalUsers();
        if (totalUsers == 0) {
            return 100.0;
        }
        long rank = userStatsRepository.calculateRank(rankingPoints);
        return Math.round((double) rank / totalUsers * 1000) / 10.0;
    }
}
