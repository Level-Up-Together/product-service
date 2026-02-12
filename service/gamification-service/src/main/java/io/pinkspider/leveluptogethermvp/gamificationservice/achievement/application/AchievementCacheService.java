package io.pinkspider.leveluptogethermvp.gamificationservice.achievement.application;

import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.Achievement;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.AchievementCategory;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.AchievementCategoryRepository;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.AchievementRepository;
import jakarta.annotation.PostConstruct;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Achievement/AchievementCategory 캐시 서비스
 * - Redis 캐시 우선 조회, 캐시 미스 시 DB fallback
 * - Admin에서 변경 시 캐시 무효화됨
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true, transactionManager = "gamificationTransactionManager")
public class AchievementCacheService {

    private final AchievementRepository achievementRepository;
    private final AchievementCategoryRepository achievementCategoryRepository;

    // ==================== Achievement ====================

    /**
     * 활성화된 모든 업적 조회
     * 캐시 키: achievements::active
     */
    @Cacheable(value = "achievements", key = "'active'", unless = "#result == null || #result.isEmpty()")
    public List<Achievement> getActiveAchievements() {
        log.info("[AchievementCacheService] DB에서 활성 Achievement 로드 (캐시 미스)");
        return achievementRepository.findByIsActiveTrue();
    }

    /**
     * 보이는 업적만 조회 (isHidden = false)
     * 캐시 키: achievements::visible
     */
    @Cacheable(value = "achievements", key = "'visible'", unless = "#result == null || #result.isEmpty()")
    public List<Achievement> getVisibleAchievements() {
        log.info("[AchievementCacheService] DB에서 보이는 Achievement 로드 (캐시 미스)");
        return achievementRepository.findVisibleAchievements();
    }

    /**
     * 카테고리 코드로 활성 업적 조회
     * 캐시 키: achievements::byCategoryCode::{categoryCode}
     */
    @Cacheable(value = "achievements", key = "'byCategoryCode::' + #categoryCode", unless = "#result == null || #result.isEmpty()")
    public List<Achievement> getAchievementsByCategoryCode(String categoryCode) {
        log.info("[AchievementCacheService] DB에서 카테고리별 Achievement 로드 (categoryCode: {}, 캐시 미스)", categoryCode);
        return achievementRepository.findByCategoryCodeAndIsActiveTrue(categoryCode);
    }

    /**
     * 미션 카테고리 ID로 활성 업적 조회
     * 캐시 키: achievements::byMissionCategoryId::{missionCategoryId}
     */
    @Cacheable(value = "achievements", key = "'byMissionCategoryId::' + #missionCategoryId", unless = "#result == null || #result.isEmpty()")
    public List<Achievement> getAchievementsByMissionCategoryId(Long missionCategoryId) {
        log.info("[AchievementCacheService] DB에서 미션 카테고리별 Achievement 로드 (missionCategoryId: {}, 캐시 미스)", missionCategoryId);
        return achievementRepository.findByMissionCategoryIdAndIsActiveTrue(missionCategoryId);
    }

    /**
     * 체크 로직 데이터 소스별 활성 업적 조회 (동적 체크용)
     * 캐시 키: achievements::byDataSource::{dataSource}
     */
    @Cacheable(value = "achievements", key = "'byDataSource::' + #dataSource", unless = "#result == null || #result.isEmpty()")
    public List<Achievement> getAchievementsByDataSource(String dataSource) {
        log.info("[AchievementCacheService] DB에서 데이터소스별 Achievement 로드 (dataSource: {}, 캐시 미스)", dataSource);
        return achievementRepository.findByCheckLogicDataSourceAndIsActiveTrue(dataSource);
    }

    /**
     * 체크 로직이 있는 모든 활성 업적 조회
     * 캐시 키: achievements::withCheckLogic
     */
    @Cacheable(value = "achievements", key = "'withCheckLogic'", unless = "#result == null || #result.isEmpty()")
    public List<Achievement> getAchievementsWithCheckLogic() {
        log.info("[AchievementCacheService] DB에서 체크로직 보유 Achievement 로드 (캐시 미스)");
        return achievementRepository.findAllWithCheckLogicAndIsActiveTrue();
    }

    // ==================== AchievementCategory ====================

    /**
     * 활성화된 모든 업적 카테고리 조회
     * 캐시 키: achievementCategories::active
     */
    @Cacheable(value = "achievementCategories", key = "'active'", unless = "#result == null || #result.isEmpty()")
    public List<AchievementCategory> getActiveCategories() {
        log.info("[AchievementCacheService] DB에서 활성 AchievementCategory 로드 (캐시 미스)");
        return achievementCategoryRepository.findByIsActiveTrueOrderBySortOrderAsc();
    }

    /**
     * 모든 업적 카테고리 조회 (비활성 포함)
     * 캐시 키: achievementCategories::all
     */
    @Cacheable(value = "achievementCategories", key = "'all'", unless = "#result == null || #result.isEmpty()")
    public List<AchievementCategory> getAllCategories() {
        log.info("[AchievementCacheService] DB에서 전체 AchievementCategory 로드 (캐시 미스)");
        return achievementCategoryRepository.findAllByOrderBySortOrderAsc();
    }

    // ==================== Cache Warmup ====================

    /**
     * 애플리케이션 시작 시 캐시 워밍업
     * - Admin보다 MVP가 먼저 시작될 경우를 대비
     */
    @PostConstruct
    public void warmUpCache() {
        try {
            List<Achievement> achievements = getActiveAchievements();
            List<AchievementCategory> categories = getActiveCategories();
            log.info("[AchievementCacheService] 캐시 워밍업 완료: achievements={}, categories={}",
                    achievements.size(), categories.size());
        } catch (Exception e) {
            log.warn("[AchievementCacheService] 캐시 워밍업 실패 (Admin 시작 시 로드됨): {}", e.getMessage());
        }
    }
}
