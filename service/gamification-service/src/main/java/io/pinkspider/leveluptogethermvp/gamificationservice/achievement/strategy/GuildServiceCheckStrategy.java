package io.pinkspider.leveluptogethermvp.gamificationservice.achievement.strategy;

import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.Achievement;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.enums.ComparisonOperator;
import io.pinkspider.leveluptogethermvp.guildservice.application.GuildQueryFacadeService;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildFacadeDto.GuildMembershipInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * GUILD_SERVICE 데이터 소스에 대한 업적 체크 전략
 * 길드 가입 여부, 길드장 여부 등을 체크합니다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GuildServiceCheckStrategy implements AchievementCheckStrategy {

    private final GuildQueryFacadeService guildQueryFacadeService;

    @Override
    public String getDataSource() {
        return "GUILD_SERVICE";
    }

    @Override
    public Object fetchCurrentValue(String userId, String dataField) {
        List<GuildMembershipInfo> memberships = guildQueryFacadeService.getUserGuildMemberships(userId);

        return switch (dataField) {
            case "isGuildMember" -> !memberships.isEmpty();
            case "isGuildMaster" -> memberships.stream()
                .anyMatch(GuildMembershipInfo::isMaster);
            default -> {
                log.warn("알 수 없는 dataField: {}", dataField);
                yield false;
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
        ComparisonOperator operator = ComparisonOperator.fromCode(achievement.getComparisonOperator());

        // Boolean 비교의 경우
        if (currentValue instanceof Boolean) {
            // requiredCount가 1이면 true, 0이면 false로 간주
            boolean targetValue = achievement.getRequiredCount() >= 1;
            boolean result = operator.compareBoolean((Boolean) currentValue, targetValue);
            log.debug("GuildService 조건 체크(Boolean): userId={}, field={}, current={}, target={}, operator={}, result={}",
                userId, dataField, currentValue, targetValue, operator, result);
            return result;
        }

        // Number 비교의 경우 (향후 확장용)
        if (currentValue instanceof Number) {
            int requiredCount = achievement.getRequiredCount();
            boolean result = operator.compare((Number) currentValue, requiredCount);
            log.debug("GuildService 조건 체크(Number): userId={}, field={}, current={}, required={}, operator={}, result={}",
                userId, dataField, currentValue, requiredCount, operator, result);
            return result;
        }

        return false;
    }
}
