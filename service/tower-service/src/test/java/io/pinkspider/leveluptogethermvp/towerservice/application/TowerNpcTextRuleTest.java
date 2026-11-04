package io.pinkspider.leveluptogethermvp.towerservice.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TowerNpcTextRuleTest {

    @Test
    @DisplayName("표시 폭: 한글·한자·가나·전각·이모지 2, 라틴·아랍 문자·숫자·공백 1 — 상한 80(한글 40자)")
    void width() {
        assertThat(TowerNpcTextRule.displayWidth("가나다")).isEqualTo(6);
        assertThat(TowerNpcTextRule.displayWidth("Hi 1")).isEqualTo(4);
        assertThat(TowerNpcTextRule.displayWidth("漢字かなカナ")).isEqualTo(12);
        assertThat(TowerNpcTextRule.displayWidth("ＡＢ")).isEqualTo(4);
        assertThat(TowerNpcTextRule.displayWidth("￥")).isEqualTo(2);
        assertThat(TowerNpcTextRule.displayWidth("مرحبا")).isEqualTo(5);
        assertThat(TowerNpcTextRule.displayWidth("🎵")).isEqualTo(2); // 대리 쌍도 한 글자
        assertThat(TowerNpcTextRule.displayWidth("ㄱ")).isEqualTo(2);
        assertThat(TowerNpcTextRule.displayWidth("ᄀ")).isEqualTo(2);
        assertThat(TowerNpcTextRule.displayWidth("「」")).isEqualTo(4);
        assertThat(TowerNpcTextRule.displayWidth("㐀")).isEqualTo(2);
        assertThat(TowerNpcTextRule.displayWidth("豈")).isEqualTo(2);

        assertThat(TowerNpcTextRule.fits("가".repeat(40))).isTrue();
        assertThat(TowerNpcTextRule.fits("가".repeat(41))).isFalse();
        assertThat(TowerNpcTextRule.fits("a".repeat(80))).isTrue();
        assertThat(TowerNpcTextRule.fits("a".repeat(81))).isFalse();
        assertThat(TowerNpcTextRule.fits(null)).isTrue();
    }
}
