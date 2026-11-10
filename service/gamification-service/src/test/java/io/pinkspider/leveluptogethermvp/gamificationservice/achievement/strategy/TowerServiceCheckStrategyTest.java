package io.pinkspider.leveluptogethermvp.gamificationservice.achievement.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.pinkspider.global.facade.TowerQueryFacade;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.Achievement;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class TowerServiceCheckStrategyTest {

    @Mock private TowerQueryFacade towerQueryFacade;
    @InjectMocks private TowerServiceCheckStrategy strategy;

    private static Achievement achievement(String field, String operator, Integer required) {
        Achievement a =
                Achievement.builder()
                        .name("탑 업적")
                        .checkLogicDataSource("TOWER_SERVICE")
                        .checkLogicDataField(field)
                        .comparisonOperator(operator)
                        .requiredCount(required)
                        .build();
        ReflectionTestUtils.setField(a, "id", 1L);
        return a;
    }

    @Test
    @DisplayName(
            "데이터 소스 TOWER_SERVICE · visitedFloorCount / highestVisitedFloor 는 파사드 값, 모르는 필드·null·조회"
                    + " 실패는 0")
    void fetchCurrentValue() {
        when(towerQueryFacade.countVisitedFloors("u1")).thenReturn(3);
        when(towerQueryFacade.highestVisitedFloor("u1")).thenReturn(5);
        assertThat(strategy.getDataSource()).isEqualTo("TOWER_SERVICE");
        assertThat(strategy.fetchCurrentValue("u1", "visitedFloorCount")).isEqualTo(3);
        assertThat(strategy.fetchCurrentValue("u1", "highestVisitedFloor")).isEqualTo(5);
        assertThat(strategy.fetchCurrentValue("u1", "unknown")).isEqualTo(0);
        assertThat(strategy.fetchCurrentValue("u1", (String) null)).isEqualTo(0);

        when(towerQueryFacade.countVisitedFloors("down")).thenThrow(new RuntimeException("db"));
        assertThat(strategy.fetchCurrentValue("down", "visitedFloorCount")).isEqualTo(0);
    }

    @Test
    @DisplayName("조건: 첫 입장(GTE 1)·세 층(GTE 3)·최고 층, 연산자 없음/오류·필드 없음은 false")
    void checkCondition() {
        when(towerQueryFacade.countVisitedFloors("u1")).thenReturn(1);
        when(towerQueryFacade.highestVisitedFloor("u1")).thenReturn(2);
        assertThat(strategy.checkCondition("u1", achievement("visitedFloorCount", "GTE", 1)))
                .isTrue();
        assertThat(strategy.checkCondition("u1", achievement("visitedFloorCount", "GTE", 3)))
                .isFalse();
        assertThat(strategy.checkCondition("u1", achievement("highestVisitedFloor", "GTE", 2)))
                .isTrue();
        assertThat(strategy.checkCondition("u1", achievement("highestVisitedFloor", "GTE", 3)))
                .isFalse();
        assertThat(strategy.checkCondition("u1", achievement("visitedFloorCount", "", 1)))
                .isFalse();
        // 모르는 연산자 코드는 ComparisonOperator.fromCode 가 GTE 로 본다 (기본값)
        assertThat(strategy.checkCondition("u1", achievement("visitedFloorCount", "WHAT", 1)))
                .isTrue();
        assertThat(strategy.checkCondition("u1", achievement("visitedFloorCount", "GTE", null)))
                .isFalse();
        assertThat(strategy.checkCondition("u1", achievement(null, "GTE", 1))).isFalse();
    }
}
