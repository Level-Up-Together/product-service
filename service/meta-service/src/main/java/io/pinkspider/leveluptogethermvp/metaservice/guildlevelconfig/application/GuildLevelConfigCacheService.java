package io.pinkspider.leveluptogethermvp.metaservice.guildlevelconfig.application;

import io.pinkspider.global.exception.CustomException;
import io.pinkspider.leveluptogethermvp.metaservice.guildlevelconfig.domain.dto.GuildLevelConfigPageResponse;
import io.pinkspider.leveluptogethermvp.metaservice.guildlevelconfig.domain.dto.GuildLevelConfigRequest;
import io.pinkspider.leveluptogethermvp.metaservice.guildlevelconfig.domain.dto.GuildLevelConfigResponse;
import io.pinkspider.leveluptogethermvp.metaservice.guildlevelconfig.domain.entity.GuildLevelConfig;
import io.pinkspider.leveluptogethermvp.metaservice.guildlevelconfig.infrastructure.GuildLevelConfigRepository;
import io.pinkspider.leveluptogethermvp.metaservice.userlevelconfig.application.UserLevelConfigCacheService;
import io.pinkspider.leveluptogethermvp.metaservice.userlevelconfig.domain.entity.UserLevelConfig;
import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * GuildLevelConfig 캐시 서비스
 * - Redis 캐시 우선 조회, 캐시 미스 시 DB fallback
 * - Admin Internal API를 통한 CRUD 지원 (auto-calc 포함)
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true, transactionManager = "metaTransactionManager")
public class GuildLevelConfigCacheService {

    private final GuildLevelConfigRepository guildLevelConfigRepository;
    private final UserLevelConfigCacheService userLevelConfigCacheService;

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
        // LUT-483: 레거시 경로에서도 레벨 판정 기준(포인트)은 자동계산으로 채운다
        int requiredPoint = calculateRequiredPoint(level);
        config.setRequiredPoint(requiredPoint);
        config.setCumulativePoint(calculateCumulativePoint(level, requiredPoint));
        config.setMaxMembers(maxMembers);
        config.setTitle(title);
        config.setDescription(description);

        return guildLevelConfigRepository.save(config);
    }

    // ========== Admin Internal API용 CRUD 메서드 ==========

    /**
     * 모든 길드 레벨 설정 Response 조회 (레벨 오름차순)
     */
    public List<GuildLevelConfigResponse> getAllLevelConfigResponses() {
        return guildLevelConfigRepository.findAllByOrderByLevelAsc().stream()
            .map(GuildLevelConfigResponse::from)
            .collect(Collectors.toList());
    }

    /**
     * 길드 레벨 설정 검색 (페이징)
     * QA-99: keyword가 비어있을 때 searchByKeyword를 호출하면 Hibernate가 NULL을 bytea로
     * binding하여 PostgreSQL grammar 에러 발생 → null/blank는 findAll로 분기.
     */
    public GuildLevelConfigPageResponse searchLevelConfigs(String keyword, Pageable pageable) {
        var page = (keyword == null || keyword.isBlank())
            ? guildLevelConfigRepository.findAll(pageable)
            : guildLevelConfigRepository.searchByKeyword(keyword, pageable);
        return GuildLevelConfigPageResponse.from(page.map(GuildLevelConfigResponse::from));
    }

    /**
     * ID로 길드 레벨 설정 조회
     */
    public GuildLevelConfigResponse getLevelConfigById(Long id) {
        GuildLevelConfig config = guildLevelConfigRepository.findById(id)
            .orElseThrow(() -> new CustomException("404", "error.guild_level.not_found"));
        return GuildLevelConfigResponse.from(config);
    }

    /**
     * 레벨 번호로 길드 레벨 설정 조회
     */
    public GuildLevelConfigResponse getLevelConfigResponseByLevel(Integer level) {
        GuildLevelConfig config = guildLevelConfigRepository.findByLevel(level)
            .orElseThrow(() -> new CustomException("404", "error.guild_level.not_found"));
        return GuildLevelConfigResponse.from(config);
    }

    /**
     * 길드 레벨 설정 생성 (auto-calc: requiredExp, cumulativeExp)
     */
    @CacheEvict(value = "guildLevelConfigs", allEntries = true)
    @Transactional(transactionManager = "metaTransactionManager")
    public GuildLevelConfigResponse createLevelConfig(GuildLevelConfigRequest request) {
        if (guildLevelConfigRepository.existsByLevel(request.getLevel())) {
            throw new CustomException("400", "error.guild_level.duplicate");
        }

        int calculatedRequiredExp = calculateRequiredExp(request.getLevel(), request.getMaxMembers());
        int calculatedCumulativeExp = calculateCumulativeExp(request.getLevel(), calculatedRequiredExp);
        int calculatedRequiredPoint = calculateRequiredPoint(request.getLevel());
        int calculatedCumulativePoint = calculateCumulativePoint(request.getLevel(), calculatedRequiredPoint);

        GuildLevelConfig config = GuildLevelConfig.builder()
            .level(request.getLevel())
            .requiredExp(calculatedRequiredExp)
            .cumulativeExp(calculatedCumulativeExp)
            .requiredPoint(calculatedRequiredPoint)
            .cumulativePoint(calculatedCumulativePoint)
            .maxMembers(request.getMaxMembers())
            .title(request.getTitle())
            .description(request.getDescription())
            .build();

        GuildLevelConfig saved = guildLevelConfigRepository.save(config);
        log.info("길드 레벨 설정 생성: level={}, requiredPoint={}, cumulativePoint={}",
            saved.getLevel(), calculatedRequiredPoint, calculatedCumulativePoint);
        return GuildLevelConfigResponse.from(saved);
    }

    /**
     * 길드 레벨 설정 수정 (auto-calc: requiredExp, cumulativeExp)
     */
    @CacheEvict(value = "guildLevelConfigs", allEntries = true)
    @Transactional(transactionManager = "metaTransactionManager")
    public GuildLevelConfigResponse updateLevelConfig(Long id, GuildLevelConfigRequest request) {
        GuildLevelConfig config = guildLevelConfigRepository.findById(id)
            .orElseThrow(() -> new CustomException("404", "error.guild_level.not_found"));

        if (!config.getLevel().equals(request.getLevel())
            && guildLevelConfigRepository.existsByLevel(request.getLevel())) {
            throw new CustomException("400", "error.guild_level.duplicate");
        }

        int calculatedRequiredExp = calculateRequiredExp(request.getLevel(), request.getMaxMembers());
        int calculatedCumulativeExp = calculateCumulativeExp(request.getLevel(), calculatedRequiredExp);
        int calculatedRequiredPoint = calculateRequiredPoint(request.getLevel());
        int calculatedCumulativePoint = calculateCumulativePoint(request.getLevel(), calculatedRequiredPoint);

        config.setLevel(request.getLevel());
        config.setRequiredExp(calculatedRequiredExp);
        config.setCumulativeExp(calculatedCumulativeExp);
        config.setRequiredPoint(calculatedRequiredPoint);
        config.setCumulativePoint(calculatedCumulativePoint);
        config.setMaxMembers(request.getMaxMembers());
        config.setTitle(request.getTitle());
        config.setDescription(request.getDescription());

        GuildLevelConfig saved = guildLevelConfigRepository.save(config);
        log.info("길드 레벨 설정 수정: id={}, level={}, requiredExp={}", id, saved.getLevel(), calculatedRequiredExp);
        return GuildLevelConfigResponse.from(saved);
    }

    /**
     * 길드 레벨 설정 삭제
     */
    @CacheEvict(value = "guildLevelConfigs", allEntries = true)
    @Transactional(transactionManager = "metaTransactionManager")
    public void deleteLevelConfig(Long id) {
        if (!guildLevelConfigRepository.existsById(id)) {
            throw new CustomException("404", "error.guild_level.not_found");
        }
        guildLevelConfigRepository.deleteById(id);
        log.info("길드 레벨 설정 삭제: id={}", id);
    }

    // ========== Auto-calculation 로직 ==========

    /**
     * 필요 경험치 계산: maxMembers * userLevelRequiredExp
     */
    private int calculateRequiredExp(int guildLevel, int maxMembers) {
        UserLevelConfig userLevelConfig = userLevelConfigCacheService.getLevelConfigByLevel(guildLevel);

        if (userLevelConfig == null) {
            log.warn("유저 레벨 {} 설정이 없어 기본값 500을 사용합니다.", guildLevel);
            return maxMembers * 500;
        }

        return maxMembers * userLevelConfig.getRequiredExp();
    }

    /**
     * 누적 경험치 계산: cumulative_exp(N) = cumulative_exp(N-1) + required_exp(N)
     */
    private int calculateCumulativeExp(int guildLevel, int currentRequiredExp) {
        if (guildLevel <= 1) {
            return currentRequiredExp;
        }

        return guildLevelConfigRepository.findByLevel(guildLevel - 1)
            .map(prevConfig -> {
                int prevCumulative = prevConfig.getCumulativeExp() != null ? prevConfig.getCumulativeExp() : 0;
                return prevCumulative + currentRequiredExp;
            })
            .orElse(currentRequiredExp);
    }

    // LUT-483: 레벨 스텝당 포인트. 활성 5명이 매일 상한(6점)까지 채우는 길드의 하루치(30점) 기준 —
    // required_point(L) = 30 × (L-1) 로 레벨이 오를수록 선형 증가한다. 포인트는 EXP 와 스케일이
    // 달라(훨씬 작음) EXP 공식(maxMembers × userExp)을 재사용할 수 없다.
    private static final int POINT_PER_LEVEL_STEP = 30;

    /** LUT-483: 필요 포인트 계산 — required_point(L) = 30 × (L-1) */
    private int calculateRequiredPoint(int guildLevel) {
        return POINT_PER_LEVEL_STEP * Math.max(0, guildLevel - 1);
    }

    /** LUT-483: 누적 포인트 계산 — cumulative_point(N) = cumulative_point(N-1) + required_point(N) */
    private int calculateCumulativePoint(int guildLevel, int currentRequiredPoint) {
        if (guildLevel <= 1) {
            return currentRequiredPoint;
        }

        return guildLevelConfigRepository.findByLevel(guildLevel - 1)
            .map(prevConfig -> {
                int prevCumulative = prevConfig.getCumulativePoint() != null ? prevConfig.getCumulativePoint() : 0;
                return prevCumulative + currentRequiredPoint;
            })
            .orElse(currentRequiredPoint);
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
