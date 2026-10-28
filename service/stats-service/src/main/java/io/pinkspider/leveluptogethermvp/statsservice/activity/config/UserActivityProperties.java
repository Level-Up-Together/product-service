package io.pinkspider.leveluptogethermvp.statsservice.activity.config;

import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** LUT-598: 활동(DAU) 수집 설정 — {@code app.stats.activity.*} */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.stats.activity")
public class UserActivityProperties {

    /** false 면 인터셉터가 아무것도 기록하지 않는다 (긴급 차단용) */
    private boolean enabled = true;

    /** DAU 에서 제외할 유저 ID — E2E 테스트 봇·운영자 계정. config-repository 환경별로 준다 */
    private Set<String> excludedUserIds = new HashSet<>();
}
