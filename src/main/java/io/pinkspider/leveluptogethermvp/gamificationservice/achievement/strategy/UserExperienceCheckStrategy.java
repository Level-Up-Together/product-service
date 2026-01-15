package io.pinkspider.leveluptogethermvp.gamificationservice.achievement.strategy;

import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.Achievement;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.UserExperience;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.enums.ComparisonOperator;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.UserExperienceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * USER_EXPERIENCE 데이터 소스에 대한 업적 체크 전략
 * 현재 레벨, 총 경험치 등을 체크합니다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserExperienceCheckStrategy implements AchievementCheckStrategy {

    private final UserExperienceRepository userExperienceRepository;

    @Override
    public String getDataSource() {
        return "USER_EXPERIENCE";
    }

    @Override
    public Object fetchCurrentValue(String userId, String dataField) {
        UserExperience userExp = userExperienceRepository.findByUserId(userId).orElse(null);
        if (userExp == null) {
            return 0;
        }

        return switch (dataField) {
            case "currentLevel" -> userExp.getCurrentLevel();
            case "totalExp" -> userExp.getTotalExp();
            case "currentExp" -> userExp.getCurrentExp();
            default -> {
                log.warn("알 수 없는 dataField: {}", dataField);
                yield 0;
            }
        };
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
        log.debug("UserExperience 조건 체크: userId={}, field={}, current={}, required={}, operator={}, result={}",
            userId, dataField, currentValue, requiredCount, operator, result);

        return result;
    }
}
