package io.pinkspider.leveluptogethermvp.metaservice.core.config;

import io.pinkspider.leveluptogethermvp.metaservice.application.MissionCategoryService;
import io.pinkspider.leveluptogethermvp.metaservice.domain.dto.MissionCategoryResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class MissionCategoryRedisConfig {

    private final MissionCategoryService missionCategoryService;

    /**
     * 앱 시작 시 미션 카테고리를 Redis 캐시에 적재 (warm-up)
     * 빈 결과가 반환되면 캐시하지 않음 (@Cacheable unless 조건으로 보호)
     */
    @EventListener(ApplicationReadyEvent.class)
    public void loadMissionCategoriesIntoCache() {
        try {
            List<MissionCategoryResponse> categories = missionCategoryService.getActiveCategories();
            if (categories.isEmpty()) {
                log.warn("Mission categories warm-up returned empty list - cache not populated (unless condition). "
                    + "Check meta_db connection and mission_category table data.");
            } else {
                log.info("Mission categories loaded into Redis cache: {} categories", categories.size());
            }
        } catch (Exception e) {
            log.warn("Failed to load mission categories into Redis cache on startup: {}", e.getMessage());
        }
    }
}
