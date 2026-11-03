package io.pinkspider.leveluptogethermvp.statsservice.kpi.domain;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * LUT-678: KPI <b>전 지표</b>에서 뺄 유저 집합 — config {@code
 * app.stats.activity.excluded-user-ids}(팀원·운영자·QA, 환경별).
 *
 * <p>DB 가 user_db·gamification_db·mission_db 로 나뉘어 있어 조인으로 거를 수 없고, 각 리더 SQL 에 {@code NOT IN
 * (:excluded)} 로 id 목록을 넘긴다. {@code NamedParameterJdbcTemplate} 은 빈 컬렉션을 {@code IN ()} 으로 펼쳐 문법 오류가
 * 나므로, 비어 있으면 어떤 id 와도 맞지 않는 센티널 하나({@link #NONE_SENTINEL})를 넘긴다 (feed 의 {@code __none__} 과 같은 수법).
 * 예전엔 이 목록이 DAU·REQUEST 리텐션(Java 필터)에만 쓰여 가입·코호트·출석·미션·구독·매출에는 팀 계정이 그대로 들어갔다.
 */
public final class ExcludedUsers {

    static final String NONE_SENTINEL = "__none__";

    private static final ExcludedUsers NONE = new ExcludedUsers(Set.of());

    private final Set<String> ids;

    private ExcludedUsers(Set<String> ids) {
        this.ids = ids;
    }

    public static ExcludedUsers of(Set<String> ids) {
        return ids == null || ids.isEmpty() ? NONE : new ExcludedUsers(Set.copyOf(ids));
    }

    public static ExcludedUsers none() {
        return NONE;
    }

    public boolean contains(String userId) {
        return userId != null && ids.contains(userId);
    }

    public boolean isEmpty() {
        return ids.isEmpty();
    }

    public Set<String> ids() {
        return ids;
    }

    /** SQL {@code NOT IN (:excluded)} 에 그대로 바인딩할 값 — 비면 센티널 1개 */
    public Collection<String> sqlValues() {
        return ids.isEmpty() ? List.of(NONE_SENTINEL) : ids;
    }
}
