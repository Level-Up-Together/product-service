package io.pinkspider.leveluptogethermvp.gamificationservice.achievement.strategy;

import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.Achievement;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.enums.ComparisonOperator;
import io.pinkspider.leveluptogethermvp.missionservice.infrastructure.MissionExecutionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * MISSION_CATEGORY 데이터 소스에 대한 업적 체크 전략
 * 특정 미션 카테고리별 완료 횟수를 체크합니다.
 *
 * dataField 형식: "category_{categoryId}" (예: "category_1", "category_2")
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MissionCategoryCheckStrategy implements AchievementCheckStrategy {

    private final MissionExecutionRepository missionExecutionRepository;

    @Override
    public String getDataSource() {
        return "MISSION_CATEGORY";
    }

    @Override
    public Object fetchCurrentValue(String userId, String dataField) {
        Long categoryId = extractCategoryId(dataField);
        if (categoryId == null) {
            log.warn("잘못된 dataField 형식: {}. 'category_{{id}}' 형식이어야 합니다.", dataField);
            return 0L;
        }

        long completedCount = missionExecutionRepository.countCompletedByUserIdAndCategoryId(userId, categoryId);
        log.debug("미션 카테고리별 완료 횟수 조회: userId={}, categoryId={}, count={}", userId, categoryId, completedCount);
        return completedCount;
    }

    @Override
    public boolean checkCondition(String userId, Achievement achievement) {
        String dataField = achievement.getCheckLogicDataField();
        if (dataField == null) {
            return false;
        }

        Object currentValue = fetchCurrentValue(userId, dataField);
        if (!(currentValue instanceof Number)) {
            return false;
        }

        ComparisonOperator operator = ComparisonOperator.fromCode(achievement.getComparisonOperator());
        int requiredCount = achievement.getRequiredCount();

        boolean result = operator.compare((Number) currentValue, requiredCount);
        log.debug("MissionCategory 조건 체크: userId={}, field={}, current={}, required={}, operator={}, result={}",
            userId, dataField, currentValue, requiredCount, operator, result);

        return result;
    }

    /**
     * dataField에서 카테고리 ID를 추출합니다.
     * 예: "category_1" -> 1L, "category_2" -> 2L
     */
    private Long extractCategoryId(String dataField) {
        if (dataField == null || !dataField.startsWith("category_")) {
            return null;
        }
        try {
            return Long.parseLong(dataField.substring("category_".length()));
        } catch (NumberFormatException e) {
            log.error("카테고리 ID 파싱 실패: {}", dataField, e);
            return null;
        }
    }
}
