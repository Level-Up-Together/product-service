package io.pinkspider.leveluptogethermvp.towerservice.presence;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerInstanceEvent;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.lang.Nullable;

/**
 * LUT-631: 인스턴스 안 한 사람의 실시간 상태 — Redis 해시 {@code tower:presence:{instanceId}} 의 값. 좌표는 배경 원본 픽셀(썸네일
 * 맨 아래 중앙). {@code connected} 는 웹소켓 연결 여부(미션 중인 유저는 끊겨도 자리를 유지하며 다른 유저에게는 연결된 것과 같이 보인다). {@code
 * autoSeated} 는 지표 분리용이라 화면에는 내리지 않는다({@link TowerPresenceView}). {@code visitorTag}(LUT-705) = 방문객
 * 세션의 공개 식별자(로그인 유저는 null) — 숨기기를 세션 기준으로 이어 간다.
 */
@JsonNaming(SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public record TowerPresence(
        Long occupantId,
        Long instanceId,
        Integer floorNumber,
        String userId,
        String kind,
        Integer visitorNo,
        String nickname,
        String picture,
        Integer level,
        List<TowerAvatarItem> items,
        Integer x,
        Integer y,
        String state,
        Boolean connected,
        Boolean autoSeated,
        LocalDateTime lastActiveAt,
        String visitorTag,
        @Nullable TowerInstanceEvent.MissionInfo mission) {

    public static final String STATE_MISSION = "MISSION";
    public static final String STATE_REST = "REST";

    public boolean isConnected() {
        return Boolean.TRUE.equals(connected);
    }

    public boolean isVisitor() {
        return "VISITOR".equals(kind);
    }

    public TowerPresence withPosition(int newX, int newY, LocalDateTime now) {
        return new TowerPresence(
                occupantId,
                instanceId,
                floorNumber,
                userId,
                kind,
                visitorNo,
                nickname,
                picture,
                level,
                items,
                newX,
                newY,
                state,
                connected,
                autoSeated,
                now,
                visitorTag,
                mission);
    }

    public TowerPresence withConnected(boolean value, LocalDateTime now) {
        return new TowerPresence(
                occupantId,
                instanceId,
                floorNumber,
                userId,
                kind,
                visitorNo,
                nickname,
                picture,
                level,
                items,
                x,
                y,
                state,
                value,
                autoSeated,
                now,
                visitorTag,
                mission);
    }

    /**
     * LUT-749: 상태와 함께 미션 정보(공개 미션만, 비공개면 null)를 바꾼다 — 휴식이면 항상 null. 입장 스냅샷이 늦게 들어온 사람에게도 미션명을 보여
     * 주려고 presence 에 둔다(예전엔 USER_STATE 이벤트에만 실렸다)
     */
    public TowerPresence withState(
            String newState, @Nullable TowerInstanceEvent.MissionInfo newMission) {
        return new TowerPresence(
                occupantId,
                instanceId,
                floorNumber,
                userId,
                kind,
                visitorNo,
                nickname,
                picture,
                level,
                items,
                x,
                y,
                newState,
                connected,
                autoSeated,
                lastActiveAt,
                visitorTag,
                STATE_MISSION.equals(newState) ? newMission : null);
    }

    /** LUT-737: 아바타(닉네임·사진·레벨·장착 아이템)만 바꾼다 — 위치·연결·상태는 그대로 */
    public TowerPresence withAvatar(
            String newNickname,
            String newPicture,
            Integer newLevel,
            List<TowerAvatarItem> newItems) {
        return new TowerPresence(
                occupantId,
                instanceId,
                floorNumber,
                userId,
                kind,
                visitorNo,
                newNickname,
                newPicture,
                newLevel,
                newItems,
                x,
                y,
                state,
                connected,
                autoSeated,
                lastActiveAt,
                visitorTag,
                mission);
    }

    public TowerPresence withLevel(int newLevel) {
        return new TowerPresence(
                occupantId,
                instanceId,
                floorNumber,
                userId,
                kind,
                visitorNo,
                nickname,
                picture,
                newLevel,
                items,
                x,
                y,
                state,
                connected,
                autoSeated,
                lastActiveAt,
                visitorTag,
                mission);
    }

    public TowerPresence withInstance(Long newInstanceId) {
        return new TowerPresence(
                occupantId,
                newInstanceId,
                floorNumber,
                userId,
                kind,
                visitorNo,
                nickname,
                picture,
                level,
                items,
                x,
                y,
                state,
                connected,
                autoSeated,
                lastActiveAt,
                visitorTag,
                mission);
    }

    public TowerPresenceView toView() {
        return new TowerPresenceView(
                occupantId,
                userId,
                kind,
                visitorNo,
                nickname,
                picture,
                level,
                items,
                x,
                y,
                state,
                visitorTag,
                STATE_MISSION.equals(state) ? mission : null);
    }
}
