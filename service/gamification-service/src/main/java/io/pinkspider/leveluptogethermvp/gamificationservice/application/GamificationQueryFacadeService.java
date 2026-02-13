package io.pinkspider.leveluptogethermvp.gamificationservice.application;

import io.pinkspider.leveluptogethermvp.gamificationservice.achievement.application.AchievementService;
import io.pinkspider.leveluptogethermvp.gamificationservice.achievement.application.TitleService;
import io.pinkspider.leveluptogethermvp.gamificationservice.achievement.application.TitleService.DetailedTitleInfo;
import io.pinkspider.leveluptogethermvp.gamificationservice.achievement.application.TitleService.TitleChangeResult;
import io.pinkspider.leveluptogethermvp.gamificationservice.achievement.application.TitleService.TitleInfo;
import io.pinkspider.leveluptogethermvp.gamificationservice.achievement.domain.dto.UserAchievementResponse;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.UserExperience;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.UserStats;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.UserTitle;
import io.pinkspider.leveluptogethermvp.gamificationservice.experience.application.UserExperienceService;
import io.pinkspider.leveluptogethermvp.gamificationservice.stats.application.UserStatsService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 외부 서비스용 게임화 읽기 전용 Facade
 * gamificationservice 외부에서 gamification_db에 직접 접근하지 않고 이 서비스를 통해 조회한다.
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true, transactionManager = "gamificationTransactionManager")
public class GamificationQueryFacadeService {

    private final TitleService titleService;
    private final UserExperienceService userExperienceService;
    private final UserStatsService userStatsService;
    private final AchievementService achievementService;

    // ========== 레벨 조회 ==========

    public int getUserLevel(String userId) {
        return userExperienceService.getUserLevel(userId);
    }

    public Map<String, Integer> getUserLevelMap(List<String> userIds) {
        return userExperienceService.getUserLevelMap(userIds);
    }

    @Transactional(transactionManager = "gamificationTransactionManager")
    public UserExperience getOrCreateUserExperience(String userId) {
        return userExperienceService.getOrCreateUserExperience(userId);
    }

    public List<Object[]> findTopExpGainersByPeriod(LocalDateTime start, LocalDateTime end, Pageable pageable) {
        return userExperienceService.findTopExpGainersByPeriod(start, end, pageable);
    }

    public List<Object[]> findTopExpGainersByCategoryAndPeriod(String categoryName, LocalDateTime start,
                                                                LocalDateTime end, Pageable pageable) {
        return userExperienceService.findTopExpGainersByCategoryAndPeriod(categoryName, start, end, pageable);
    }

    // ========== 칭호 조회 ==========

    public TitleInfo getCombinedEquippedTitleInfo(String userId) {
        return titleService.getCombinedEquippedTitleInfo(userId);
    }

    public DetailedTitleInfo getDetailedEquippedTitleInfo(String userId) {
        return titleService.getDetailedEquippedTitleInfo(userId);
    }

    public Map<String, String> getEquippedLeftTitleNameMap(List<String> userIds) {
        return titleService.getEquippedLeftTitleNameMap(userIds);
    }

    public Map<String, List<UserTitle>> getEquippedTitleEntitiesByUserIds(List<String> userIds) {
        return titleService.getEquippedTitleEntitiesByUserIds(userIds);
    }

    public List<UserTitle> getEquippedTitleEntitiesByUserId(String userId) {
        return titleService.getEquippedTitleEntitiesByUserId(userId);
    }

    public List<UserTitle> getUserTitleEntitiesWithTitle(String userId) {
        return titleService.getUserTitleEntitiesWithTitle(userId);
    }

    public long countUserTitles(String userId) {
        return titleService.countUserTitles(userId);
    }

    @Transactional(transactionManager = "gamificationTransactionManager")
    public TitleChangeResult changeTitles(String userId, Long leftUserTitleId, Long rightUserTitleId) {
        return titleService.changeTitles(userId, leftUserTitleId, rightUserTitleId);
    }

    // ========== 칭호 부여 ==========

    @Transactional(transactionManager = "gamificationTransactionManager")
    public void grantAndEquipDefaultTitles(String userId) {
        titleService.grantAndEquipDefaultTitles(userId);
    }

    // ========== 스탯 조회 ==========

    public UserStats getOrCreateUserStats(String userId) {
        return userStatsService.getOrCreateUserStats(userId);
    }

    public Double calculateRankingPercentile(long rankingPoints) {
        return userStatsService.calculateRankingPercentile(rankingPoints);
    }

    // ========== 업적 조회 ==========

    public List<UserAchievementResponse> getUserAchievements(String userId) {
        return achievementService.getUserAchievements(userId);
    }

}
