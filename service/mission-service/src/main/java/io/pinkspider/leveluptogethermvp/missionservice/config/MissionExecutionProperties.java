package io.pinkspider.leveluptogethermvp.missionservice.config;

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
    private int baseExp = 10;

    /**
     * 자동종료 경고 알림 (N분 전)
     */
    private int warningMinutesBeforeAutoEnd = 10;
}
