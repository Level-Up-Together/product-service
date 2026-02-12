package io.pinkspider.leveluptogethermvp.metaservice.userlevelconfig.application;

import io.pinkspider.leveluptogethermvp.metaservice.userlevelconfig.domain.entity.UserLevelConfig;
import io.pinkspider.leveluptogethermvp.metaservice.userlevelconfig.infrastructure.UserLevelConfigRepository;
import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * UserLevelConfig 캐시 서비스
 * - Redis 캐시 우선 조회, 캐시 미스 시 DB fallback
 * - Admin에서 변경 시 캐시 무효화됨
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true, transactionManager = "metaTransactionManager")
public class UserLevelConfigCacheService {

    private final UserLevelConfigRepository userLevelConfigRepository;

    /**
     * 모든 레벨 설정 조회 (레벨 오름차순)
     * 캐시 키: userLevelConfigs::all
     */
    @Cacheable(value = "userLevelConfigs", key = "'all'", unless = "#result == null || #result.isEmpty()")
    public List<UserLevelConfig> getAllLevelConfigs() {
        log.info("[UserLevelConfigCacheService] DB에서 전체 UserLevelConfig 로드 (캐시 미스)");
        return userLevelConfigRepository.findAllByOrderByLevelAsc();
    }

    /**
     * 특정 레벨 설정 조회
     * 캐시 키: userLevelConfigs::{level}
     */
    @Cacheable(value = "userLevelConfigs", key = "#level", unless = "#result == null")
    public UserLevelConfig getLevelConfigByLevel(Integer level) {
        log.info("[UserLevelConfigCacheService] DB에서 UserLevelConfig 로드 (레벨: {}, 캐시 미스)", level);
        return userLevelConfigRepository.findByLevel(level).orElse(null);
    }

    /**
     * 총 경험치로 현재 레벨 조회
     * - 이 메서드는 캐싱하지 않음 (경험치별로 다양한 값이 들어올 수 있어 캐시 효율 낮음)
     * - 대신 getAllLevelConfigs()를 캐시하고 메모리에서 계산
     */
    public Optional<UserLevelConfig> getLevelByTotalExp(Integer totalExp) {
        List<UserLevelConfig> configs = getAllLevelConfigs();
        return configs.stream()
                .filter(config -> config.getCumulativeExp() != null && config.getCumulativeExp() <= totalExp)
                .reduce((first, second) -> second); // 마지막 (가장 높은 레벨) 반환
    }

    /**
     * 최대 레벨 조회
     * - 캐시된 전체 목록에서 계산
     */
    public Integer getMaxLevel() {
        List<UserLevelConfig> configs = getAllLevelConfigs();
        return configs.isEmpty() ? 0 : configs.get(configs.size() - 1).getLevel();
    }

    /**
     * 레벨 설정 생성/수정 (Admin용, 캐시 무효화 포함)
     */
    @CacheEvict(value = "userLevelConfigs", allEntries = true)
    @Transactional(transactionManager = "metaTransactionManager")
    public UserLevelConfig createOrUpdateLevelConfig(Integer level, Integer requiredExp,
                                                     Integer cumulativeExp) {
        UserLevelConfig config = userLevelConfigRepository.findByLevel(level)
            .orElse(UserLevelConfig.builder().level(level).build());

        config.setRequiredExp(requiredExp);
        config.setCumulativeExp(cumulativeExp);

        return userLevelConfigRepository.save(config);
    }

    /**
     * 애플리케이션 시작 시 캐시 워밍업
     * - Admin보다 MVP가 먼저 시작될 경우를 대비
     */
    @PostConstruct
    public void warmUpCache() {
        try {
            List<UserLevelConfig> configs = getAllLevelConfigs();
            log.info("[UserLevelConfigCacheService] 캐시 워밍업 완료: {} 개 레벨 설정 로드", configs.size());
        } catch (Exception e) {
            log.warn("[UserLevelConfigCacheService] 캐시 워밍업 실패 (Admin 시작 시 로드됨): {}", e.getMessage());
        }
    }
}
