package io.pinkspider.leveluptogethermvp.gamificationservice.achievement.strategy;

import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.Achievement;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.UserStats;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.enums.ComparisonOperator;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.UserStatsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * USER_STATS 데이터 소스에 대한 업적 체크 전략
 * 미션 완료 횟수, 스트릭, 업적 완료 수 등을 체크합니다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserStatsCheckStrategy implements AchievementCheckStrategy {

    private final UserStatsRepository userStatsRepository;

    @Override
    public String getDataSource() {
        return "USER_STATS";
    }

    @Override
    public Object fetchCurrentValue(String userId, String dataField) {
        UserStats userStats = userStatsRepository.findByUserId(userId).orElse(null);
        if (userStats == null) {
            return 0;
        }

        return switch (dataField) {
            case "totalMissionCompletions" -> userStats.getTotalMissionCompletions();
            case "totalMissionFullCompletions" -> userStats.getTotalMissionFullCompletions();
            case "totalGuildMissionCompletions" -> userStats.getTotalGuildMissionCompletions();
            case "currentStreak" -> userStats.getCurrentStreak();
            case "maxStreak" -> userStats.getMaxStreak();
            case "totalAchievementsCompleted" -> userStats.getTotalAchievementsCompleted();
            case "totalTitlesAcquired" -> userStats.getTotalTitlesAcquired();
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
        log.debug("UserStats 조건 체크: userId={}, field={}, current={}, required={}, operator={}, result={}",
            userId, dataField, currentValue, requiredCount, operator, result);

        return result;
    }
}
