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

    /**
     * KPI 전 지표에서 제외할 유저 ID — 팀원·운영자·QA·E2E 봇. config-repository 환경별로 준다(운영은 LUT-678 목록).
     *
     * <p>인터셉터는 이 유저의 활동을 기록하지 않고(DAU), KPI 리더는 가입·탈퇴·활성·코호트 모수·출석·미션·구독·매출 SQL 전부에서 {@code NOT IN}
     * 으로 뺀다({@code ExcludedUsers}, LUT-678). 목록을 바꾸면 재기동 뒤 {@code POST /api/internal/kpi/rebuild} 로
     * 소급 — 단 인터셉터가 이미 버린 활동은 되살아나지 않는다.
     */
    private Set<String> excludedUserIds = new HashSet<>();
}
