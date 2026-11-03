package io.pinkspider.leveluptogethermvp.towerservice.domain.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.towerservice.presence.TowerPresenceView;
import java.time.LocalDateTime;
import org.springframework.lang.Nullable;

/**
 * LUT-631/632: 탑 인스턴스 토픽({@code /topic/tower/instances/{id}}) 브로드캐스트 페이로드. 클라이언트는 {@code type} 으로
 * 가른다.
 *
 * <ul>
 *   <li>{@code ENTER} — {@code occupant}(스냅샷과 같은 모양) 가 방에 나타났다.
 *   <li>{@code LEAVE} — {@code occupant_id} 가 방을 떠났다.
 *   <li>{@code MOVE} — {@code occupant_id} 의 목적지 {@code x,y}(서버가 마스크로 검증·보정한 값).
 *   <li>{@code LEVEL} — {@code user_id} 의 레벨이 {@code level} 로 바뀌었다(머리 위 이름표).
 *   <li>{@code USER_STATE} — {@code state} MISSION/REST, {@code mission} 은 공개 미션일 때만.
 *   <li>{@code MISSION_COMPLETED} — 완료 연출 + {@code cheer_window_seconds} 응원 창.
 * </ul>
 *
 * 차단 관계 숨김은 수신 측(스냅샷 필터·클라이언트)이 처리한다.
 */
@JsonNaming(SnakeCaseStrategy.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TowerInstanceEvent(
        String type,
        String userId,
        Long occupantId,
        Integer x,
        Integer y,
        Integer level,
        TowerPresenceView occupant,
        String state,
        MissionInfo mission,
        Integer cheerWindowSeconds,
        LocalDateTime occurredAt) {

    public static final String TYPE_ENTER = "ENTER";
    public static final String TYPE_LEAVE = "LEAVE";
    public static final String TYPE_MOVE = "MOVE";
    public static final String TYPE_LEVEL = "LEVEL";
    public static final String TYPE_USER_STATE = "USER_STATE";
    public static final String TYPE_MISSION_COMPLETED = "MISSION_COMPLETED";
    public static final String STATE_MISSION = "MISSION";
    public static final String STATE_REST = "REST";
    public static final int CHEER_WINDOW_SECONDS = 10;

    @JsonNaming(SnakeCaseStrategy.class)
    public record MissionInfo(String title, Long categoryId) {}

    public static TowerInstanceEvent enter(TowerPresenceView occupant, LocalDateTime now) {
        return new TowerInstanceEvent(
                TYPE_ENTER,
                occupant.userId(),
                occupant.occupantId(),
                null,
                null,
                null,
                occupant,
                null,
                null,
                null,
                now);
    }

    public static TowerInstanceEvent leave(
            Long occupantId, @Nullable String userId, LocalDateTime now) {
        return new TowerInstanceEvent(
                TYPE_LEAVE, userId, occupantId, null, null, null, null, null, null, null, now);
    }

    public static TowerInstanceEvent move(Long occupantId, int x, int y, LocalDateTime now) {
        return new TowerInstanceEvent(
                TYPE_MOVE, null, occupantId, x, y, null, null, null, null, null, now);
    }

    public static TowerInstanceEvent level(String userId, int level, LocalDateTime now) {
        return new TowerInstanceEvent(
                TYPE_LEVEL, userId, null, null, null, level, null, null, null, null, now);
    }

    public static TowerInstanceEvent missionState(
            String userId, @Nullable MissionInfo mission, LocalDateTime now) {
        return new TowerInstanceEvent(
                TYPE_USER_STATE,
                userId,
                null,
                null,
                null,
                null,
                null,
                STATE_MISSION,
                mission,
                null,
                now);
    }

    public static TowerInstanceEvent restState(String userId, LocalDateTime now) {
        return new TowerInstanceEvent(
                TYPE_USER_STATE, userId, null, null, null, null, null, STATE_REST, null, null, now);
    }

    public static TowerInstanceEvent missionCompleted(String userId, LocalDateTime now) {
        return new TowerInstanceEvent(
                TYPE_MISSION_COMPLETED,
                userId,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                CHEER_WINDOW_SECONDS,
                now);
    }
}
