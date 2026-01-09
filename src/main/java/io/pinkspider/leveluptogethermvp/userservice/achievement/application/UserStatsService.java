package io.pinkspider.leveluptogethermvp.userservice.achievement.application;

import io.pinkspider.leveluptogethermvp.userservice.achievement.domain.dto.UserStatsResponse;
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
@Transactional(readOnly = true)
public class UserStatsService {

    private final UserStatsRepository userStatsRepository;

    @Transactional
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

    @Transactional
    public void recordMissionCompletion(String userId, boolean isGuildMission) {
        UserStats stats = getOrCreateUserStats(userId);
        stats.incrementMissionCompletion();
        if (isGuildMission) {
            stats.incrementGuildMissionCompletion();
        }
        stats.updateStreak(LocalDate.now());
        log.debug("미션 완료 기록: userId={}, totalCompletions={}", userId, stats.getTotalMissionCompletions());
    }

    @Transactional
    public void recordMissionFullCompletion(String userId) {
        UserStats stats = getOrCreateUserStats(userId);
        stats.incrementMissionFullCompletion();
        log.debug("미션 전체 완료 기록: userId={}, totalFullCompletions={}", userId, stats.getTotalMissionFullCompletions());
    }

    @Transactional
    public void recordAchievementCompleted(String userId) {
        UserStats stats = getOrCreateUserStats(userId);
        stats.incrementAchievementCompleted();
    }

    @Transactional
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
}
