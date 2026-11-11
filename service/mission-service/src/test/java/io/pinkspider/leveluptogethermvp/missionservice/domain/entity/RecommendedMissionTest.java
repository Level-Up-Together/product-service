package io.pinkspider.leveluptogethermvp.missionservice.domain.entity;

import static org.assertj.core.api.Assertions.assertThat;

import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionExecutionMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("RecommendedMission (LUT-782)")
class RecommendedMissionTest {

    private static RecommendedMission timer() {
        return RecommendedMission.create(
                "5분 독서", "Read", " ", null, 5L, MissionExecutionMode.TIMED, 5, "📖", true, 3);
    }

    @Test
    @DisplayName("빈 다른 언어 이름은 null 로, 순서·활성은 그대로")
    void create() {
        RecommendedMission m = timer();
        assertThat(m.getTitleEn()).isEqualTo("Read");
        assertThat(m.getTitleJa()).isNull();
        assertThat(m.getTitleAr()).isNull();
        assertThat(m.getTargetMinutes()).isEqualTo(5);
        assertThat(m.getSortOrder()).isEqualTo(3);
        assertThat(m.isActive()).isTrue();
    }

    @Test
    @DisplayName("체크 미션은 목표 시간을 저장하지 않는다")
    void simpleDropsMinutes() {
        RecommendedMission m = timer();
        m.update("책상 정리", null, null, null, null, MissionExecutionMode.SIMPLE, 10, "🧹", false);
        assertThat(m.getTargetMinutes()).isNull();
        assertThat(m.getExecutionMode()).isEqualTo(MissionExecutionMode.SIMPLE);
        assertThat(m.isActive()).isFalse();
    }

    @Test
    @DisplayName("요청 언어 이름, 없으면 한국어")
    void localizedTitle() {
        RecommendedMission m = timer();
        assertThat(m.getLocalizedTitle("en-US")).isEqualTo("Read");
        assertThat(m.getLocalizedTitle("ja")).isEqualTo("5분 독서");
        assertThat(m.getLocalizedTitle(null)).isEqualTo("5분 독서");
    }

    @Test
    @DisplayName("활성 토글·순서 변경")
    void toggleAndOrder() {
        RecommendedMission m = timer();
        m.toggleActive();
        assertThat(m.isActive()).isFalse();
        m.changeSortOrder(9);
        assertThat(m.getSortOrder()).isEqualTo(9);
    }
}
