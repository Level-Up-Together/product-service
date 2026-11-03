package io.pinkspider.leveluptogethermvp.towerservice.domain.dto;

import static org.assertj.core.api.Assertions.assertThat;

import io.pinkspider.global.event.MissionExecutionStartedEvent;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TowerInstanceEventTest {

    @Test
    @DisplayName("미션 정보는 PUBLIC 만 브로드캐스트에 실리고, 나머지 공개범위는 '미션 중'만")
    void missionInfoVisibility() {
        MissionExecutionStartedEvent event =
                new MissionExecutionStartedEvent(
                        "u1", 10L, 20L, "PINNED", "명상", 4L, "FRIENDS_ONLY", LocalDateTime.now());
        TowerMissionInfo info = TowerMissionInfo.from(event);
        assertThat(info.executionKind()).isEqualTo("PINNED");
        assertThat(info.isPublic()).isFalse();
        assertThat(info.toBroadcast()).isNull();

        TowerMissionInfo pub = new TowerMissionInfo(1L, 2L, "REGULAR", "달리기", 3L, "PUBLIC");
        assertThat(pub.toBroadcast()).isEqualTo(new TowerInstanceEvent.MissionInfo("달리기", 3L));
    }

    @Test
    @DisplayName("이벤트 팩토리: 상태·완료 연출(10초)")
    void factories() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 3, 12, 0);
        TowerInstanceEvent rest = TowerInstanceEvent.restState("u1", now);
        assertThat(rest.type()).isEqualTo("USER_STATE");
        assertThat(rest.state()).isEqualTo("REST");
        assertThat(rest.mission()).isNull();
        assertThat(rest.occurredAt()).isEqualTo(now);

        TowerInstanceEvent done = TowerInstanceEvent.missionCompleted("u1", now);
        assertThat(done.type()).isEqualTo("MISSION_COMPLETED");
        assertThat(done.cheerWindowSeconds()).isEqualTo(10);
        assertThat(done.state()).isNull();
    }
}
