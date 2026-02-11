package io.pinkspider.leveluptogethermvp.gamificationservice.stats.application;

import io.pinkspider.leveluptogethermvp.gamificationservice.stats.domain.dto.UserStatsResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.UserStats;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.UserStatsRepository;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true, transactionManager = "gamificationTransactionManager")
public class UserStatsService {

    private final UserStatsRepository userStatsRepository;

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
        stats.updateStreak(LocalDate.now());
        log.debug("미션 완료 기록: userId={}, totalCompletions={}", userId, stats.getTotalMissionCompletions());
    }

    @Transactional(transactionManager = "gamificationTransactionManager")
    public void recordMissionFullCompletion(String userId, int durationDays) {
        UserStats stats = getOrCreateUserStats(userId);
        stats.incrementMissionFullCompletion(durationDays);
        log.debug("미션 전체 완료 기록: userId={}, totalFullCompletions={}, durationDays={}, maxDuration={}",
            userId, stats.getTotalMissionFullCompletions(), durationDays, stats.getMaxCompletedMissionDuration());
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

    /**
     * 기존 사용자의 좋아요/친구 카운터 초기화 (일회성 마이그레이션용)
     */
    @Transactional(transactionManager = "gamificationTransactionManager")
    public void syncCountersForUser(String userId, long likesReceived, int friendCount) {
        UserStats stats = getOrCreateUserStats(userId);
        stats.setTotalLikesReceived(likesReceived);
        stats.setFriendCount(friendCount);
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
