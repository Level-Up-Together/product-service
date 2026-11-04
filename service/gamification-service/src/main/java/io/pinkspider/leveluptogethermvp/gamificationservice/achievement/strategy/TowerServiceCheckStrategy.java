package io.pinkspider.leveluptogethermvp.gamificationservice.achievement.strategy;

import io.pinkspider.global.facade.TowerQueryFacade;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.Achievement;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.enums.ComparisonOperator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * LUT-634: TOWER_SERVICE 데이터 소스 — 레벨업 탑 첫 입장 업적. 값은 towerservice 의 tower_floor_visit(유저×층 첫 입장
 * 기록)에서 파사드로 읽는다. 재판정 트리거는 {@code TowerFloorFirstEnteredEvent}(층에 처음 들어간 순간).
 *
 * <ul>
 *   <li>{@code visitedFloorCount} — 처음 들어가 본 층 수 (예: "탑 첫 입장" = GTE 1, "세 층 탐험" = GTE 3)
 *   <li>{@code highestVisitedFloor} — 들어가 본 층 중 가장 높은 번호 (예: "5층 도달" = GTE 5)
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TowerServiceCheckStrategy implements AchievementCheckStrategy {

    public static final String DATA_SOURCE = "TOWER_SERVICE";
    public static final String FIELD_VISITED_FLOOR_COUNT = "visitedFloorCount";
    public static final String FIELD_HIGHEST_VISITED_FLOOR = "highestVisitedFloor";

    private final @Lazy TowerQueryFacade towerQueryFacade;

    @Override
    public String getDataSource() {
        return DATA_SOURCE;
    }

    @Override
    public Object fetchCurrentValue(String userId, String dataField) {
        if (dataField == null) return 0;
        try {
            return switch (dataField) {
                case FIELD_VISITED_FLOOR_COUNT -> towerQueryFacade.countVisitedFloors(userId);
                case FIELD_HIGHEST_VISITED_FLOOR -> towerQueryFacade.highestVisitedFloor(userId);
                default -> {
                    log.warn("알 수 없는 TOWER_SERVICE dataField: {}", dataField);
                    yield 0;
                }
            };
        } catch (Exception e) {
            log.warn(
                    "탑 입장 기록 조회 실패: userId={}, field={}, error={}",
                    userId,
                    dataField,
                    e.getMessage());
            return 0;
        }
    }

    @Override
    public boolean checkCondition(String userId, Achievement achievement) {
        Object currentValue = fetchCurrentValue(userId, achievement.getCheckLogicDataField());
        if (!(currentValue instanceof Number number)) return false;
        ComparisonOperator operator = resolveOperator(achievement);
        Integer required = achievement.getRequiredCount();
        if (operator == null || required == null) return false;
        return operator.compare(number, required);
    }

    /** QA-178 와 같은 이유 — 잘못된 연산자 코드로 부모 트랜잭션을 rollback-only 로 만들지 않는다 */
    private ComparisonOperator resolveOperator(Achievement achievement) {
        String code = achievement.getComparisonOperator();
        if (code == null || code.isBlank()) return null;
        try {
            return ComparisonOperator.fromCode(code);
        } catch (Exception e) {
            log.warn(
                    "업적 comparisonOperator 파싱 실패: achievementId={}, code={}",
                    achievement.getId(),
                    code);
            return null;
        }
    }
}
