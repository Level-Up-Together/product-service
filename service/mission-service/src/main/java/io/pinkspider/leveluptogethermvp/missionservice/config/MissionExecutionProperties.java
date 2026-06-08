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
     * 자동종료까지 허용되는 최대 수행 시간 (분).
     * 이 시간을 초과하면 미션이 자동 종료되며 baseExp 가 부여된다.
     */
    private int maxExecutionMinutes = 240;

    /**
     * 자동종료 시 부여되는 경험치 (분당 1 EXP × maxExecutionMinutes 기준 default).
     */
    private int baseExp = 240;

    /**
     * 자동종료 경고 알림 시점 목록 (분 단위, 시작 후 경과 시간)
     * 예: [180, 230] → 3시간 경과 시 + 3시간50분 경과 시 경고 (자동종료 4시간 기준)
     */
    private List<Integer> warningMinutesAfterStart = List.of(180, 230);

    /**
     * @deprecated warningMinutesAfterStart 사용
     */
    private int warningMinutesBeforeAutoEnd = 10;
}
