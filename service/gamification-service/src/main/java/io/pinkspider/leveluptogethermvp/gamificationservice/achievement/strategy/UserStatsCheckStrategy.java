package io.pinkspider.leveluptogethermvp.gamificationservice.achievement.strategy;

import io.pinkspider.global.facade.GuildQueryFacade;
import io.pinkspider.global.facade.dto.GuildMembershipInfo;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.Achievement;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.UserStats;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.enums.ComparisonOperator;
import io.pinkspider.leveluptogethermvp.gamificationservice.infrastructure.UserStatsRepository;
import java.util.List;
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
    private final GuildQueryFacade guildQueryFacade;

    @Override
    public String getDataSource() {
        return "USER_STATS";
    }

    @Override
    public Object fetchCurrentValue(String userId, String dataField) {
        // 길드 마스터 여부는 user_stats 컬럼이 아니므로 별도 처리
        if ("isGuildMaster".equals(dataField)) {
            return isGuildMaster(userId) ? 1 : 0;
        }

        UserStats userStats = userStatsRepository.findByUserId(userId).orElse(null);
        if (userStats == null) {
            return 0;
        }

        return switch (dataField) {
            case "totalMissionCompletions" -> userStats.getTotalMissionCompletions();
            case "totalMissionFullCompletions" -> userStats.getTotalMissionFullCompletions();
            // 길드 미션 카운트: 마스터 데이터의 alias("guildMissionCount") + 표준 필드명 모두 지원
            case "totalGuildMissionCompletions", "guildMissionCount" -> userStats.getTotalGuildMissionCompletions();
            case "currentStreak" -> userStats.getCurrentStreak();
            // 최대 연속일: alias("maxStreakDays") + 표준 필드명 모두 지원
            case "maxStreak", "maxStreakDays" -> userStats.getMaxStreak();
            case "totalAchievementsCompleted" -> userStats.getTotalAchievementsCompleted();
            case "totalTitlesAcquired" -> userStats.getTotalTitlesAcquired();
            case "maxCompletedMissionDuration" -> userStats.getMaxCompletedMissionDuration();
            case "guildJoinCount" -> userStats.getGuildJoinCount();
            case "friendCount" -> userStats.getFriendCount();
            // 받은 좋아요 수: alias("receivedLikeCount") + 표준 필드명 모두 지원
            case "totalLikesReceived", "receivedLikeCount" -> userStats.getTotalLikesReceived();
            // 받은 댓글 수: alias("receivedCommentCount") + 표준 필드명 모두 지원
            case "totalCommentsReceived", "receivedCommentCount", "commentsReceived" ->
                userStats.getTotalCommentsReceived();
            default -> {
                log.warn("알 수 없는 dataField: {}", dataField);
                yield 0;
            }
        };
    }

    private boolean isGuildMaster(String userId) {
        try {
            List<GuildMembershipInfo> memberships = guildQueryFacade.getUserGuildMemberships(userId);
            return memberships != null && memberships.stream().anyMatch(GuildMembershipInfo::isMaster);
        } catch (Exception e) {
            log.warn("길드 마스터 여부 조회 실패: userId={}, error={}", userId, e.getMessage());
            return false;
        }
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
