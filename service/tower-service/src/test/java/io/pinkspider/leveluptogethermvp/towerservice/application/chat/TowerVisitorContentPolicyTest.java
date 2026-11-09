package io.pinkspider.leveluptogethermvp.towerservice.application.chat;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("TowerVisitorContentPolicy (LUT-633/705) — 방문객 URL·전화번호·메신저 아이디 차단")
class TowerVisitorContentPolicyTest {
    @Test
    void detects() {
        assertThat(TowerVisitorContentPolicy.violation("놀러와 https://x.com/a")).contains("url");
        assertThat(TowerVisitorContentPolicy.violation("www.site.kr 보셈")).contains("url");
        assertThat(TowerVisitorContentPolicy.violation("example.com")).contains("url");
        assertThat(TowerVisitorContentPolicy.violation("연락 010-1234-5678")).contains("phone");
        assertThat(TowerVisitorContentPolicy.violation("01012345678")).contains("phone");
        assertThat(TowerVisitorContentPolicy.violation("카톡 아이디 abc")).contains("messenger");
        assertThat(TowerVisitorContentPolicy.violation("insta @my_name")).contains("messenger");
        assertThat(TowerVisitorContentPolicy.violation("telegram ok")).contains("messenger");
    }

    @Test
    void allows() {
        assertThat(TowerVisitorContentPolicy.violation("안녕하세요 반가워요")).isEmpty();
        assertThat(TowerVisitorContentPolicy.violation("레벨 12 달성!")).isEmpty();
        assertThat(TowerVisitorContentPolicy.violation("점심 1시 30분")).isEmpty();
        assertThat(TowerVisitorContentPolicy.violation(null)).isEmpty();
    }
}
