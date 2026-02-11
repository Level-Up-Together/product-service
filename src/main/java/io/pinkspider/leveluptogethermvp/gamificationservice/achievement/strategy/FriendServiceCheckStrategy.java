package io.pinkspider.leveluptogethermvp.gamificationservice.achievement.strategy;

import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.Achievement;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.UserStats;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.enums.ComparisonOperator;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.UserStatsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * FRIEND_SERVICE 데이터 소스에 대한 업적 체크 전략
 * 친구 수 등을 체크합니다.
 * - gamification_db의 UserStats 카운터에서 조회 (크로스-서비스 DB 접근 제거)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FriendServiceCheckStrategy implements AchievementCheckStrategy {

    private final UserStatsRepository userStatsRepository;

    @Override
    public String getDataSource() {
        return "FRIEND_SERVICE";
    }

    @Override
    public Object fetchCurrentValue(String userId, String dataField) {
        return switch (dataField) {
            case "friendCount" -> userStatsRepository.findByUserId(userId)
                .map(UserStats::getFriendCount).orElse(0);
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
        log.debug("FriendService 조건 체크: userId={}, field={}, current={}, required={}, operator={}, result={}",
            userId, dataField, currentValue, requiredCount, operator, result);

        return result;
    }
}
