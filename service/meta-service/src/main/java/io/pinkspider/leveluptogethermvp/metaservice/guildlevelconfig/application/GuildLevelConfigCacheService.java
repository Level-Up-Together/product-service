package io.pinkspider.leveluptogethermvp.metaservice.guildlevelconfig.application;

import io.pinkspider.leveluptogethermvp.metaservice.guildlevelconfig.domain.entity.GuildLevelConfig;
import io.pinkspider.leveluptogethermvp.metaservice.guildlevelconfig.infrastructure.GuildLevelConfigRepository;
import jakarta.annotation.PostConstruct;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * GuildLevelConfig 캐시 서비스
 * - Redis 캐시 우선 조회, 캐시 미스 시 DB fallback
 * - Admin에서 변경 시 캐시 무효화됨
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true, transactionManager = "metaTransactionManager")
public class GuildLevelConfigCacheService {

    private final GuildLevelConfigRepository guildLevelConfigRepository;

    /**
     * 모든 길드 레벨 설정 조회 (레벨 오름차순)
     * 캐시 키: guildLevelConfigs::all
     */
    @Cacheable(value = "guildLevelConfigs", key = "'all'", unless = "#result == null || #result.isEmpty()")
    public List<GuildLevelConfig> getAllLevelConfigs() {
        log.info("[GuildLevelConfigCacheService] DB에서 전체 GuildLevelConfig 로드 (캐시 미스)");
        return guildLevelConfigRepository.findAllByOrderByLevelAsc();
    }

    /**
     * 특정 레벨 설정 조회
     * 캐시 키: guildLevelConfigs::{level}
     */
    @Cacheable(value = "guildLevelConfigs", key = "#level", unless = "#result == null")
    public GuildLevelConfig getLevelConfigByLevel(Integer level) {
        log.info("[GuildLevelConfigCacheService] DB에서 GuildLevelConfig 로드 (레벨: {}, 캐시 미스)", level);
        return guildLevelConfigRepository.findByLevel(level).orElse(null);
    }

    /**
     * 최대 레벨 조회
     * - 캐시된 전체 목록에서 계산
     */
    public Integer getMaxLevel() {
        List<GuildLevelConfig> configs = getAllLevelConfigs();
        return configs.isEmpty() ? 0 : configs.get(configs.size() - 1).getLevel();
    }

    /**
     * 길드 레벨 설정 생성/수정 (캐시 무효화 포함)
     */
    @CacheEvict(value = "guildLevelConfigs", allEntries = true)
    @Transactional(transactionManager = "metaTransactionManager")
    public GuildLevelConfig createOrUpdateLevelConfig(Integer level, Integer requiredExp,
                                                      Integer cumulativeExp, Integer maxMembers,
                                                      String title, String description) {
        GuildLevelConfig config = guildLevelConfigRepository.findByLevel(level)
            .orElse(GuildLevelConfig.builder().level(level).build());

        config.setRequiredExp(requiredExp);
        config.setCumulativeExp(cumulativeExp);
        config.setMaxMembers(maxMembers);
        config.setTitle(title);
        config.setDescription(description);

        return guildLevelConfigRepository.save(config);
    }

    /**
     * 애플리케이션 시작 시 캐시 워밍업
     */
    @PostConstruct
    public void warmUpCache() {
        try {
            List<GuildLevelConfig> configs = getAllLevelConfigs();
            log.info("[GuildLevelConfigCacheService] 캐시 워밍업 완료: {} 개 길드 레벨 설정 로드", configs.size());
        } catch (Exception e) {
            log.warn("[GuildLevelConfigCacheService] 캐시 워밍업 실패 (Admin 시작 시 로드됨): {}", e.getMessage());
        }
    }
}
