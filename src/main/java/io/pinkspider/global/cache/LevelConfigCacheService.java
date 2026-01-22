package io.pinkspider.global.cache;

import io.pinkspider.leveluptogethermvp.gamificationservice.levelconfig.domain.entity.LevelConfig;
import io.pinkspider.leveluptogethermvp.gamificationservice.levelconfig.infrastructure.LevelConfigRepository;
import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * LevelConfig 캐시 서비스
 * - Redis 캐시 우선 조회, 캐시 미스 시 DB fallback
 * - Admin에서 변경 시 캐시 무효화됨
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true, transactionManager = "gamificationTransactionManager")
public class LevelConfigCacheService {

    private final LevelConfigRepository levelConfigRepository;

    /**
     * 모든 레벨 설정 조회 (레벨 오름차순)
     * 캐시 키: levelConfigs::all
     */
    @Cacheable(value = "levelConfigs", key = "'all'", unless = "#result == null || #result.isEmpty()")
    public List<LevelConfig> getAllLevelConfigs() {
        log.info("[LevelConfigCacheService] DB에서 전체 LevelConfig 로드 (캐시 미스)");
        return levelConfigRepository.findAllByOrderByLevelAsc();
    }

    /**
     * 특정 레벨 설정 조회
     * 캐시 키: levelConfigs::{level}
     */
    @Cacheable(value = "levelConfigs", key = "#level", unless = "#result == null")
    public LevelConfig getLevelConfigByLevel(Integer level) {
        log.info("[LevelConfigCacheService] DB에서 LevelConfig 로드 (레벨: {}, 캐시 미스)", level);
        return levelConfigRepository.findByLevel(level).orElse(null);
    }

    /**
     * 총 경험치로 현재 레벨 조회
     * - 이 메서드는 캐싱하지 않음 (경험치별로 다양한 값이 들어올 수 있어 캐시 효율 낮음)
     * - 대신 getAllLevelConfigs()를 캐시하고 메모리에서 계산
     */
    public Optional<LevelConfig> getLevelByTotalExp(Integer totalExp) {
        List<LevelConfig> configs = getAllLevelConfigs();
        return configs.stream()
                .filter(config -> config.getCumulativeExp() != null && config.getCumulativeExp() <= totalExp)
                .reduce((first, second) -> second); // 마지막 (가장 높은 레벨) 반환
    }

    /**
     * 최대 레벨 조회
     * - 캐시된 전체 목록에서 계산
     */
    public Integer getMaxLevel() {
        List<LevelConfig> configs = getAllLevelConfigs();
        return configs.isEmpty() ? 0 : configs.get(configs.size() - 1).getLevel();
    }

    /**
     * 애플리케이션 시작 시 캐시 워밍업
     * - Admin보다 MVP가 먼저 시작될 경우를 대비
     */
    @PostConstruct
    public void warmUpCache() {
        try {
            List<LevelConfig> configs = getAllLevelConfigs();
            log.info("[LevelConfigCacheService] 캐시 워밍업 완료: {} 개 레벨 설정 로드", configs.size());
        } catch (Exception e) {
            log.warn("[LevelConfigCacheService] 캐시 워밍업 실패 (Admin 시작 시 로드됨): {}", e.getMessage());
        }
    }
}
