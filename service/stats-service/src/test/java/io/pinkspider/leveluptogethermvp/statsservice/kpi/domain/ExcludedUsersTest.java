package io.pinkspider.leveluptogethermvp.statsservice.kpi.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ExcludedUsers 테스트 (LUT-678)")
class ExcludedUsersTest {

    @Test
    @DisplayName("비어 있으면(null·빈 집합) SQL 바인딩 값은 어떤 id 와도 맞지 않는 센티널 1개 — IN () 문법 오류 방지")
    void emptyBindsSentinel() {
        assertThat(ExcludedUsers.of(null).isEmpty()).isTrue();
        assertThat(ExcludedUsers.of(Set.of()).sqlValues())
                .containsExactly(ExcludedUsers.NONE_SENTINEL);
        assertThat(ExcludedUsers.none().sqlValues()).containsExactly("__none__");
        assertThat(ExcludedUsers.none().contains("u1")).isFalse();
        assertThat(ExcludedUsers.none().contains(null)).isFalse();
    }

    @Test
    @DisplayName("목록이 있으면 그 id 들을 그대로 바인딩하고 contains 로 판정한다 (원본 집합과 분리된 복사본)")
    void idsAreBoundAsIs() {
        Set<String> source = new HashSet<>(Set.of("ops-1", "qa-2"));
        ExcludedUsers excluded = ExcludedUsers.of(source);
        source.add("later");

        assertThat(excluded.isEmpty()).isFalse();
        assertThat(excluded.ids()).containsExactlyInAnyOrder("ops-1", "qa-2");
        assertThat(excluded.sqlValues()).containsExactlyInAnyOrder("ops-1", "qa-2");
        assertThat(excluded.contains("ops-1")).isTrue();
        assertThat(excluded.contains("later")).isFalse();
    }
}
