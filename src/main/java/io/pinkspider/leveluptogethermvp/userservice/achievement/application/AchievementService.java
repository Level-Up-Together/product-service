package io.pinkspider.leveluptogethermvp.userservice.achievement.application;

import io.pinkspider.leveluptogethermvp.userservice.achievement.domain.dto.AchievementResponse;
import io.pinkspider.leveluptogethermvp.userservice.achievement.domain.dto.UserAchievementResponse;
import io.pinkspider.leveluptogethermvp.userservice.achievement.domain.entity.Achievement;
import io.pinkspider.leveluptogethermvp.userservice.achievement.domain.entity.UserAchievement;
import io.pinkspider.leveluptogethermvp.userservice.achievement.domain.enums.AchievementCategory;
import io.pinkspider.leveluptogethermvp.userservice.achievement.domain.enums.AchievementType;
import io.pinkspider.leveluptogethermvp.userservice.achievement.infrastructure.AchievementRepository;
import io.pinkspider.leveluptogethermvp.userservice.achievement.infrastructure.UserAchievementRepository;
import io.pinkspider.leveluptogethermvp.userservice.experience.application.UserExperienceService;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.entity.ExperienceHistory.ExpSourceType;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AchievementService {

    private final AchievementRepository achievementRepository;
    private final UserAchievementRepository userAchievementRepository;
    private final UserStatsService userStatsService;
    private final UserExperienceService userExperienceService;
    private final TitleService titleService;

    // 업적 목록 조회
    public List<AchievementResponse> getAllAchievements() {
        return achievementRepository.findVisibleAchievements().stream()
            .map(AchievementResponse::from)
            .toList();
    }

    public List<AchievementResponse> getAchievementsByCategory(AchievementCategory category) {
        return achievementRepository.findVisibleAchievementsByCategory(category).stream()
            .map(AchievementResponse::from)
            .toList();
    }

    // 유저의 업적 목록 조회
    public List<UserAchievementResponse> getUserAchievements(String userId) {
        return userAchievementRepository.findByUserIdWithAchievement(userId).stream()
            .map(UserAchievementResponse::from)
            .toList();
    }

    public List<UserAchievementResponse> getCompletedAchievements(String userId) {
        return userAchievementRepository.findCompletedByUserId(userId).stream()
            .map(UserAchievementResponse::from)
            .toList();
    }

    public List<UserAchievementResponse> getInProgressAchievements(String userId) {
        return userAchievementRepository.findInProgressByUserId(userId).stream()
            .map(UserAchievementResponse::from)
            .toList();
    }

    public List<UserAchievementResponse> getClaimableAchievements(String userId) {
        return userAchievementRepository.findClaimableByUserId(userId).stream()
            .map(UserAchievementResponse::from)
            .toList();
    }

    // 업적 진행도 업데이트
    @Transactional
    public void updateAchievementProgress(String userId, AchievementType type, int count) {
        Achievement achievement = achievementRepository.findByAchievementType(type).orElse(null);
        if (achievement == null || !achievement.getIsActive()) {
            return;
        }

        UserAchievement userAchievement = userAchievementRepository
            .findByUserIdAndAchievementType(userId, type)
            .orElseGet(() -> createUserAchievement(userId, achievement));

        if (userAchievement.getIsCompleted()) {
            return; // 이미 완료된 업적
        }

        userAchievement.setCount(count);

        if (userAchievement.getIsCompleted()) {
            userStatsService.recordAchievementCompleted(userId);
            log.info("업적 달성! userId={}, achievement={}", userId, type.getDisplayName());
        }
    }

    @Transactional
    public void incrementAchievementProgress(String userId, AchievementType type) {
        Achievement achievement = achievementRepository.findByAchievementType(type).orElse(null);
        if (achievement == null || !achievement.getIsActive()) {
            return;
        }

        UserAchievement userAchievement = userAchievementRepository
            .findByUserIdAndAchievementType(userId, type)
            .orElseGet(() -> createUserAchievement(userId, achievement));

        if (userAchievement.getIsCompleted()) {
            return;
        }

        userAchievement.incrementCount();

        if (userAchievement.getIsCompleted()) {
            userStatsService.recordAchievementCompleted(userId);
            log.info("업적 달성! userId={}, achievement={}", userId, type.getDisplayName());
        }
    }

    // 업적 보상 수령
    @Transactional
    public UserAchievementResponse claimReward(String userId, Long achievementId) {
        UserAchievement userAchievement = userAchievementRepository
            .findByUserIdAndAchievementId(userId, achievementId)
            .orElseThrow(() -> new IllegalArgumentException("업적을 찾을 수 없습니다."));

        userAchievement.claimReward();

        Achievement achievement = userAchievement.getAchievement();

        // 경험치 보상
        if (achievement.getRewardExp() > 0) {
            userExperienceService.addExperience(
                userId,
                achievement.getRewardExp(),
                ExpSourceType.ACHIEVEMENT,
                achievement.getId(),
                "업적 달성 보상: " + achievement.getName()
            );
        }

        // 칭호 보상
        if (achievement.getRewardTitleId() != null) {
            titleService.grantTitle(userId, achievement.getRewardTitleId());
        }

        log.info("업적 보상 수령: userId={}, achievement={}", userId, achievement.getName());
        return UserAchievementResponse.from(userAchievement);
    }

    // 미션 관련 업적 체크
    @Transactional
    public void checkMissionAchievements(String userId, int totalCompletions, boolean isGuildMission) {
        // 일반 미션 완료 업적
        checkAndUpdateAchievement(userId, AchievementType.FIRST_MISSION_COMPLETE, totalCompletions);
        checkAndUpdateAchievement(userId, AchievementType.MISSION_COMPLETE_10, totalCompletions);
        checkAndUpdateAchievement(userId, AchievementType.MISSION_COMPLETE_50, totalCompletions);
        checkAndUpdateAchievement(userId, AchievementType.MISSION_COMPLETE_100, totalCompletions);
        checkAndUpdateAchievement(userId, AchievementType.MISSION_COMPLETE_500, totalCompletions);

        // 길드 미션 업적
        if (isGuildMission) {
            var stats = userStatsService.getOrCreateUserStats(userId);
            int guildCompletions = stats.getTotalGuildMissionCompletions();
            checkAndUpdateAchievement(userId, AchievementType.GUILD_MISSION_COMPLETE_10, guildCompletions);
            checkAndUpdateAchievement(userId, AchievementType.GUILD_MISSION_COMPLETE_50, guildCompletions);
            checkAndUpdateAchievement(userId, AchievementType.GUILD_MISSION_COMPLETE_100, guildCompletions);
        }
    }

    @Transactional
    public void checkMissionFullCompletionAchievements(String userId, int totalFullCompletions) {
        checkAndUpdateAchievement(userId, AchievementType.MISSION_FULL_COMPLETE_1, totalFullCompletions);
        checkAndUpdateAchievement(userId, AchievementType.MISSION_FULL_COMPLETE_10, totalFullCompletions);
    }

    @Transactional
    public void checkStreakAchievements(String userId, int currentStreak) {
        checkAndUpdateAchievement(userId, AchievementType.STREAK_3_DAYS, currentStreak);
        checkAndUpdateAchievement(userId, AchievementType.STREAK_7_DAYS, currentStreak);
        checkAndUpdateAchievement(userId, AchievementType.STREAK_14_DAYS, currentStreak);
        checkAndUpdateAchievement(userId, AchievementType.STREAK_30_DAYS, currentStreak);
        checkAndUpdateAchievement(userId, AchievementType.STREAK_100_DAYS, currentStreak);
    }

    @Transactional
    public void checkLevelAchievements(String userId, int level) {
        checkAndUpdateAchievement(userId, AchievementType.REACH_LEVEL_5, level);
        checkAndUpdateAchievement(userId, AchievementType.REACH_LEVEL_10, level);
        checkAndUpdateAchievement(userId, AchievementType.REACH_LEVEL_20, level);
        checkAndUpdateAchievement(userId, AchievementType.REACH_LEVEL_50, level);
        checkAndUpdateAchievement(userId, AchievementType.REACH_LEVEL_100, level);
    }

    @Transactional
    public void checkGuildJoinAchievement(String userId) {
        incrementAchievementProgress(userId, AchievementType.FIRST_GUILD_JOIN);
    }

    @Transactional
    public void checkGuildMasterAchievement(String userId) {
        incrementAchievementProgress(userId, AchievementType.GUILD_MASTER);
    }

    // 소셜 관련 업적 체크
    @Transactional
    public void checkFriendAchievements(String userId, int totalFriends) {
        checkAndUpdateAchievement(userId, AchievementType.FIRST_FRIEND, totalFriends);
        checkAndUpdateAchievement(userId, AchievementType.FRIENDS_10, totalFriends);
        checkAndUpdateAchievement(userId, AchievementType.FRIENDS_50, totalFriends);
    }

    @Transactional
    public void checkLikeAchievements(String userId, int totalLikes) {
        checkAndUpdateAchievement(userId, AchievementType.FIRST_LIKE, totalLikes);
        checkAndUpdateAchievement(userId, AchievementType.LIKES_100, totalLikes);
    }

    private void checkAndUpdateAchievement(String userId, AchievementType type, int count) {
        updateAchievementProgress(userId, type, count);
    }

    private UserAchievement createUserAchievement(String userId, Achievement achievement) {
        UserAchievement userAchievement = UserAchievement.builder()
            .userId(userId)
            .achievement(achievement)
            .currentCount(0)
            .build();
        return userAchievementRepository.save(userAchievement);
    }
}
