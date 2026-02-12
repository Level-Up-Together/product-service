package io.pinkspider.leveluptogethermvp.metaservice.attendancerewardconfig.application;

import io.pinkspider.leveluptogethermvp.metaservice.attendancerewardconfig.domain.entity.AttendanceRewardConfig;
import io.pinkspider.leveluptogethermvp.metaservice.attendancerewardconfig.domain.enums.AttendanceRewardType;
import io.pinkspider.leveluptogethermvp.metaservice.attendancerewardconfig.infrastructure.AttendanceRewardConfigRepository;
import jakarta.annotation.PostConstruct;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AttendanceRewardConfig 캐시 서비스
 * - Redis 캐시 우선 조회, 캐시 미스 시 DB fallback
 * - Admin에서 변경 시 캐시 무효화됨
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true, transactionManager = "metaTransactionManager")
public class AttendanceRewardConfigCacheService {

    private final AttendanceRewardConfigRepository rewardConfigRepository;

    /**
     * 모든 활성 출석 보상 설정 조회 (requiredDays 오름차순)
     * 캐시 키: attendanceRewardConfigs::all
     */
    @Cacheable(value = "attendanceRewardConfigs", key = "'all'", unless = "#result == null || #result.isEmpty()")
    public List<AttendanceRewardConfig> getAllActiveConfigs() {
        log.info("[AttendanceRewardConfigCacheService] DB에서 전체 AttendanceRewardConfig 로드 (캐시 미스)");
        return rewardConfigRepository.findByIsActiveTrueOrderByRequiredDaysAsc();
    }

    /**
     * 특정 보상 타입 설정 조회
     * 캐시 키: attendanceRewardConfigs::{rewardType}
     */
    @Cacheable(value = "attendanceRewardConfigs", key = "#rewardType.name()", unless = "#result == null")
    public AttendanceRewardConfig getConfigByRewardType(AttendanceRewardType rewardType) {
        log.info("[AttendanceRewardConfigCacheService] DB에서 AttendanceRewardConfig 로드 (타입: {}, 캐시 미스)", rewardType);
        return rewardConfigRepository.findByRewardTypeAndIsActiveTrue(rewardType).orElse(null);
    }

    /**
     * 기본 보상 설정 초기화 (데이터 없을 때 1회성)
     */
    @CacheEvict(value = "attendanceRewardConfigs", allEntries = true)
    @Transactional(transactionManager = "metaTransactionManager")
    public void initializeDefaultRewardConfigs() {
        if (rewardConfigRepository.count() > 0) {
            return;
        }

        for (AttendanceRewardType type : AttendanceRewardType.values()) {
            AttendanceRewardConfig config = AttendanceRewardConfig.builder()
                .rewardType(type)
                .requiredDays(getRequiredDays(type))
                .rewardExp(type.getDefaultExp())
                .description(type.getDisplayName())
                .build();
            rewardConfigRepository.save(config);
        }

        log.info("출석 보상 설정 초기화 완료");
    }

    private Integer getRequiredDays(AttendanceRewardType type) {
        return switch (type) {
            case DAILY -> 1;
            case CONSECUTIVE_3 -> 3;
            case CONSECUTIVE_7 -> 7;
            case CONSECUTIVE_14 -> 14;
            case CONSECUTIVE_30 -> 30;
            case MONTHLY_COMPLETE -> 28;
            case SPECIAL_DAY -> 1;
        };
    }

    /**
     * 애플리케이션 시작 시 캐시 워밍업
     */
    @PostConstruct
    public void warmUpCache() {
        try {
            List<AttendanceRewardConfig> configs = getAllActiveConfigs();
            log.info("[AttendanceRewardConfigCacheService] 캐시 워밍업 완료: {} 개 출석 보상 설정 로드", configs.size());
        } catch (Exception e) {
            log.warn("[AttendanceRewardConfigCacheService] 캐시 워밍업 실패 (Admin 시작 시 로드됨): {}", e.getMessage());
        }
    }
}
