package io.pinkspider.leveluptogethermvp.gamificationservice.achievement.strategy;

import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.Achievement;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.enums.ComparisonOperator;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.UserCategoryExperienceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * USER_CATEGORY_EXPERIENCE 데이터 소스에 대한 업적 체크 전략
 * 특정 미션 카테고리별 누적 경험치를 체크합니다.
 *
 * dataField 형식: "category_{categoryId}" (예: "category_1", "category_2")
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserCategoryExperienceCheckStrategy implements AchievementCheckStrategy {

    private final UserCategoryExperienceRepository userCategoryExperienceRepository;

    @Override
    public String getDataSource() {
        return "USER_CATEGORY_EXPERIENCE";
    }

    @Override
    public Object fetchCurrentValue(String userId, String dataField) {
        // dataField가 'category_{id}' 형식인 경우만 처리. 'categoryExp' 형식은 Achievement 컨텍스트가 필요하므로
        // fetchCurrentValue(userId, achievement) 오버라이드 경로를 사용해야 한다.
        Long categoryId = extractCategoryId(dataField);
        if (categoryId == null) {
            log.warn("dataField={}만으로 카테고리 ID를 결정할 수 없습니다. " +
                "'category_{{id}}' 형식을 사용하거나 fetchCurrentValue(userId, achievement) 경로로 호출하세요.", dataField);
            return 0L;
        }
        return loadCategoryExp(userId, categoryId);
    }

    @Override
    public Object fetchCurrentValue(String userId, Achievement achievement) {
        Long categoryId = resolveCategoryId(achievement);
        if (categoryId == null) {
            log.warn("USER_CATEGORY_EXPERIENCE: 카테고리 ID 결정 실패. achievementId={}, dataField={}, missionCategoryId={}",
                achievement.getId(), achievement.getCheckLogicDataField(), achievement.getMissionCategoryId());
            return 0L;
        }
        return loadCategoryExp(userId, categoryId);
    }

    @Override
    public boolean checkCondition(String userId, Achievement achievement) {
        Long categoryId = resolveCategoryId(achievement);
        if (categoryId == null) {
            return false;
        }

        Long currentValue = loadCategoryExp(userId, categoryId);

        ComparisonOperator operator = ComparisonOperator.fromCode(achievement.getComparisonOperator());
        int requiredCount = achievement.getRequiredCount();

        boolean result = operator.compare(currentValue, requiredCount);
        log.debug("UserCategoryExperience 조건 체크: userId={}, categoryId={}, current={}, required={}, operator={}, result={}",
            userId, categoryId, currentValue, requiredCount, operator, result);

        return result;
    }

    private Long loadCategoryExp(String userId, Long categoryId) {
        Long totalExp = userCategoryExperienceRepository.findByUserIdAndCategoryId(userId, categoryId)
            .map(exp -> exp.getTotalExp())
            .orElse(0L);
        log.debug("카테고리별 경험치 조회: userId={}, categoryId={}, totalExp={}", userId, categoryId, totalExp);
        return totalExp;
    }

    /**
     * Achievement 컨텍스트에서 카테고리 ID를 결정.
     * 1) dataField='category_{id}' 형식이면 dataField에서 추출
     * 2) dataField='categoryExp' 또는 그 외 일반 키워드면 Achievement.missionCategoryId 사용
     */
    private Long resolveCategoryId(Achievement achievement) {
        String dataField = achievement.getCheckLogicDataField();
        Long fromDataField = extractCategoryId(dataField);
        if (fromDataField != null) {
            return fromDataField;
        }
        return achievement.getMissionCategoryId();
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
