package io.pinkspider.leveluptogethermvp.gamificationservice.achievement.strategy;

import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 업적 체크 전략 레지스트리
 * 데이터 소스에 따라 적절한 전략을 제공합니다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AchievementCheckStrategyRegistry {

    private final List<AchievementCheckStrategy> strategies;
    private final Map<String, AchievementCheckStrategy> strategyMap = new HashMap<>();

    @PostConstruct
    public void init() {
        for (AchievementCheckStrategy strategy : strategies) {
            strategyMap.put(strategy.getDataSource(), strategy);
            log.info("업적 체크 전략 등록: dataSource={}", strategy.getDataSource());
        }
    }

    /**
     * 데이터 소스에 해당하는 전략을 반환합니다.
     *
     * @param dataSource 데이터 소스 코드
     * @return 해당 전략, 없으면 null
     */
    public AchievementCheckStrategy getStrategy(String dataSource) {
        return strategyMap.get(dataSource);
    }

    /**
     * 데이터 소스에 해당하는 전략이 등록되어 있는지 확인합니다.
     *
     * @param dataSource 데이터 소스 코드
     * @return 등록 여부
     */
    public boolean hasStrategy(String dataSource) {
        return strategyMap.containsKey(dataSource);
    }
}
