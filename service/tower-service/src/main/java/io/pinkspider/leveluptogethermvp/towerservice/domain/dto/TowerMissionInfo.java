package io.pinkspider.leveluptogethermvp.towerservice.domain.dto;

import io.pinkspider.global.event.MissionExecutionStartedEvent;
import org.springframework.lang.Nullable;

/**
 * LUT-632: 자리에 연결되는 미션 수행 정보. {@code visibility} 가 PUBLIC 일 때만 다른 유저에게 미션명·카테고리를 내보낸다(비공개는 "미션
 * 중"만).
 */
public record TowerMissionInfo(
        Long missionId,
        Long executionId,
        String executionKind,
        String title,
        Long categoryId,
        String visibility) {

    public static TowerMissionInfo from(MissionExecutionStartedEvent e) {
        return new TowerMissionInfo(
                e.missionId(),
                e.executionId(),
                e.executionKind(),
                e.missionTitle(),
                e.categoryId(),
                e.visibility());
    }

    public boolean isPublic() {
        return "PUBLIC".equals(visibility);
    }

    /** 다른 유저에게 보낼 수 있는 정보만 — 비공개 미션은 null */
    @Nullable
    public TowerInstanceEvent.MissionInfo toBroadcast() {
        return isPublic() ? new TowerInstanceEvent.MissionInfo(title, categoryId) : null;
    }
}
