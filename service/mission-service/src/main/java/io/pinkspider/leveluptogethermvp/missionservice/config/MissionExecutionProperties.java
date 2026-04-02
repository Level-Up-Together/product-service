package io.pinkspider.leveluptogethermvp.missionservice.config;

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 미션 수행 관련 설정
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "mission.execution")
public class MissionExecutionProperties {

    /**
     * 기본 경험치 (2시간 초과 시 부여)
     */
    private int baseExp = 20;

    /**
     * 자동종료 경고 알림 시점 목록 (분 단위, 시작 후 경과 시간)
     * 예: [60, 110] → 1시간 경과 시 + 1시간50분 경과 시 경고
     */
    private List<Integer> warningMinutesAfterStart = List.of(60, 110);

    /**
     * @deprecated warningMinutesAfterStart 사용
     */
    private int warningMinutesBeforeAutoEnd = 10;
}
