package io.pinkspider.leveluptogethermvp.towerservice.domain.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.LocalDateTime;
import org.springframework.lang.Nullable;

/**
 * LUT-632: 탑 인스턴스 토픽({@code /topic/tower/instances/{id}}) 브로드캐스트 페이로드.
 *
 * <ul>
 *   <li>{@code USER_STATE} — 유저 상태 전환. {@code state} MISSION/REST, {@code mission} 은 공개 미션일 때만(카테고리
 *       아이콘 + 미션명), 비공개는 null("미션 중"만 표시).
 *   <li>{@code MISSION_COMPLETED} — 완료 연출 + {@code cheer_window_seconds}(10초) 응원 창.
 * </ul>
 *
 * 입장·퇴장·이동({@code ENTER/LEAVE/MOVE})은 LUT-631 이 같은 토픽에 보탠다. 차단 관계 숨김은 수신 측(스냅샷·클라이언트, LUT-631/639)이
 * 처리한다.
 */
@JsonNaming(SnakeCaseStrategy.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TowerInstanceEvent(
        String type,
        String userId,
        String state,
        MissionInfo mission,
        Integer cheerWindowSeconds,
        LocalDateTime occurredAt) {

    public static final String TYPE_USER_STATE = "USER_STATE";
    public static final String TYPE_MISSION_COMPLETED = "MISSION_COMPLETED";
    public static final String STATE_MISSION = "MISSION";
    public static final String STATE_REST = "REST";
    public static final int CHEER_WINDOW_SECONDS = 10;

    @JsonNaming(SnakeCaseStrategy.class)
    public record MissionInfo(String title, Long categoryId) {}

    public static TowerInstanceEvent missionState(
            String userId, @Nullable MissionInfo mission, LocalDateTime now) {
        return new TowerInstanceEvent(TYPE_USER_STATE, userId, STATE_MISSION, mission, null, now);
    }

    public static TowerInstanceEvent restState(String userId, LocalDateTime now) {
        return new TowerInstanceEvent(TYPE_USER_STATE, userId, STATE_REST, null, null, now);
    }

    public static TowerInstanceEvent missionCompleted(String userId, LocalDateTime now) {
        return new TowerInstanceEvent(
                TYPE_MISSION_COMPLETED, userId, null, null, CHEER_WINDOW_SECONDS, now);
    }
}
