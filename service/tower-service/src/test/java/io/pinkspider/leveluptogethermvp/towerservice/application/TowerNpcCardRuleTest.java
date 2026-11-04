package io.pinkspider.leveluptogethermvp.towerservice.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TowerNpcCardRuleTest {

    @Test
    @DisplayName("LUT-733 앱 경로: / 로 시작하는 앱 경로만 — //·://·공백·역슬래시·제어 문자·300자 초과는 거부")
    void appPath() {
        assertThat(TowerNpcCardRule.isAppPath("/shop/subscription")).isTrue();
        assertThat(TowerNpcCardRule.isAppPath("/shop?item=3&from=tower")).isTrue();
        assertThat(TowerNpcCardRule.isAppPath("/")).isTrue();
        assertThat(TowerNpcCardRule.isAppPath("/" + "a".repeat(299))).isTrue();
        assertThat(TowerNpcCardRule.isAppPath("/" + "a".repeat(300))).isFalse();
        assertThat(TowerNpcCardRule.isAppPath(null)).isFalse();
        assertThat(TowerNpcCardRule.isAppPath("")).isFalse();
        assertThat(TowerNpcCardRule.isAppPath("shop")).isFalse();
        assertThat(TowerNpcCardRule.isAppPath("//evil.com")).isFalse();
        assertThat(TowerNpcCardRule.isAppPath("/redirect?to=https://evil.com")).isFalse();
        assertThat(TowerNpcCardRule.isAppPath("/\\evil.com")).isFalse();
        assertThat(TowerNpcCardRule.isAppPath("/a b")).isFalse();
        assertThat(TowerNpcCardRule.isAppPath("/a\tb")).isFalse();
        assertThat(TowerNpcCardRule.isAppPath("/a\u0000b")).isFalse();
    }
}
