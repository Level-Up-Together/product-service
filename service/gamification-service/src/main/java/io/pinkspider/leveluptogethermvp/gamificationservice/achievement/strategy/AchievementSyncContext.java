package io.pinkspider.leveluptogethermvp.gamificationservice.achievement.strategy;

import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.UserAchievement;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.UserCategoryExperience;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.UserExperience;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.UserStats;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * syncUserAchievements 1회 실행 동안 strategies 가 공유하는 사전 로드 데이터.
 *
 * 기존 구현은 각 Strategy가 achievement 마다 DB를 조회 (~316 업적 × 2회 = 632+ 쿼리).
 * 이 컨텍스트는 1회 sync 시작 시점에 source 테이블을 한 번씩만 로드하여 Strategy 가 in-memory
 * 평가하도록 한다.
 *
 * 캐시되는 데이터:
 *   - UserStats (1행)
 *   - UserExperience (1행)
 *   - UserCategoryExperience (categoryId → totalExp 맵)
 *   - 길드 마스터 여부 (boolean)
 *   - 사용자의 user_achievement 전체 (achievementId → row 맵)
 */
public final class AchievementSyncContext {

    private final String userId;
    private final UserStats userStats;
    private final UserExperience userExperience;
    private final Map<Long, Long> categoryExpByCategoryId;
    private final boolean guildMaster;
    private final Map<Long, UserAchievement> userAchievementsByAchievementId;

    public AchievementSyncContext(
        String userId,
        UserStats userStats,
        UserExperience userExperience,
        List<UserCategoryExperience> categoryExperiences,
        boolean guildMaster,
        List<UserAchievement> userAchievements
    ) {
        this.userId = userId;
        this.userStats = userStats;
        this.userExperience = userExperience;
        this.categoryExpByCategoryId = toCategoryExpMap(categoryExperiences);
        this.guildMaster = guildMaster;
        this.userAchievementsByAchievementId = toUserAchievementMap(userAchievements);
    }

    public String getUserId() {
        return userId;
    }

    public UserStats getUserStats() {
        return userStats;
    }

    public UserExperience getUserExperience() {
        return userExperience;
    }

    /** categoryId 에 해당하는 누적 경험치를 반환 (없으면 0). */
    public long getCategoryExp(Long categoryId) {
        if (categoryId == null) {
            return 0L;
        }
        return categoryExpByCategoryId.getOrDefault(categoryId, 0L);
    }

    public boolean isGuildMaster() {
        return guildMaster;
    }

    /** achievementId 에 해당하는 user_achievement 행을 반환 (없으면 null). */
    public UserAchievement findUserAchievement(Long achievementId) {
        return userAchievementsByAchievementId.get(achievementId);
    }

    /** UserAchievement 신규 생성 시 컨텍스트에 등록 (이후 lookup 가능). */
    public void registerUserAchievement(UserAchievement ua) {
        userAchievementsByAchievementId.put(ua.getAchievement().getId(), ua);
    }

    private static Map<Long, Long> toCategoryExpMap(List<UserCategoryExperience> items) {
        if (items == null || items.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> map = new HashMap<>(items.size());
        for (UserCategoryExperience uce : items) {
            map.put(uce.getCategoryId(), uce.getTotalExp());
        }
        return map;
    }

    private static Map<Long, UserAchievement> toUserAchievementMap(List<UserAchievement> items) {
        if (items == null || items.isEmpty()) {
            return new HashMap<>();
        }
        Map<Long, UserAchievement> map = new HashMap<>(items.size());
        for (UserAchievement ua : items) {
            if (ua.getAchievement() != null) {
                map.put(ua.getAchievement().getId(), ua);
            }
        }
        return map;
    }
}
