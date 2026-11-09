package io.pinkspider.leveluptogethermvp.towerservice.application.exception;

import org.springframework.http.HttpStatus;

/** LUT-630: 탑 입장·퇴장 거절. 서버가 최종 판정한다(프론트 가드만으로는 우회된다). */
public class TowerEntryException extends TowerException {

    public static final String FLOOR_NOT_AVAILABLE = "150201";
    public static final String LOGIN_REQUIRED = "150202";
    public static final String LEVEL_INSUFFICIENT = "150203";
    public static final String VISITOR_KEY_REQUIRED = "150204";
    public static final String NOT_IN_INSTANCE = "150205";

    /** LUT-701: 없거나 끝난 대기표 — 처음부터 다시 입장을 요청한다 */
    public static final String QUEUE_TICKET_NOT_FOUND = "150206";

    /** LUT-738: 방 이동 거절 — 가득 참 · 10초 안 재이동(value.retry_after_seconds) · 다른 층의 방 · 없는 방 */
    public static final String ROOM_FULL = "150207";

    public static final String ROOM_MOVE_TOO_SOON = "150208";
    public static final String ROOM_OTHER_FLOOR = "150209";
    public static final String ROOM_NOT_FOUND = "150210";

    private TowerEntryException(String code, String messageKey, HttpStatus status) {
        super(code, messageKey, status);
    }

    private TowerEntryException(
            String code, String messageKey, HttpStatus status, Object[] args, Object detail) {
        super(code, messageKey, status, args, detail);
    }

    /** LUT-738: 고른 방이 가득 찼다 — 다른 방으로 대신 보내지 않는다 */
    public static TowerEntryException roomFull() {
        return new TowerEntryException(ROOM_FULL, "error.tower.room.full", HttpStatus.CONFLICT);
    }

    /** LUT-738: 방 이동은 10초에 한 번 — 남은 초를 문구와 {@code value.retry_after_seconds} 로 */
    public static TowerEntryException roomMoveTooSoon(long remainingSeconds) {
        return new TowerEntryException(
                ROOM_MOVE_TOO_SOON,
                "error.tower.room.move_too_soon",
                HttpStatus.TOO_MANY_REQUESTS,
                new Object[] {remainingSeconds},
                java.util.Map.of("retry_after_seconds", remainingSeconds));
    }

    public static TowerEntryException roomOtherFloor() {
        return new TowerEntryException(
                ROOM_OTHER_FLOOR, "error.tower.room.other_floor", HttpStatus.BAD_REQUEST);
    }

    public static TowerEntryException roomNotFound() {
        return new TowerEntryException(
                ROOM_NOT_FOUND, "error.tower.room.not_found", HttpStatus.NOT_FOUND);
    }

    /** 없는 층 또는 비공개 층 */
    public static TowerEntryException floorNotAvailable() {
        return new TowerEntryException(
                FLOOR_NOT_AVAILABLE, "error.tower.entry.floor_not_available", HttpStatus.NOT_FOUND);
    }

    /** 비로그인 비허용 층 — 입장 레벨이 제한 없음이어도 로그인이 필요하다. 401 은 웹의 토큰 재발급 경로를 타므로 403 */
    public static TowerEntryException loginRequired() {
        return new TowerEntryException(
                LOGIN_REQUIRED, "error.tower.entry.login_required", HttpStatus.FORBIDDEN);
    }

    public static TowerEntryException levelInsufficient() {
        return new TowerEntryException(
                LEVEL_INSUFFICIENT, "error.tower.entry.level_insufficient", HttpStatus.FORBIDDEN);
    }

    /** LUT-631: 그 방의 점유자가 아닌데 스냅샷을 요청 */
    public static TowerEntryException queueTicketNotFound() {
        return new TowerEntryException(
                QUEUE_TICKET_NOT_FOUND, "error.tower.queue.ticket_not_found", HttpStatus.NOT_FOUND);
    }

    public static TowerEntryException notInInstance() {
        return new TowerEntryException(
                NOT_IN_INSTANCE, "error.tower.presence.not_in_instance", HttpStatus.FORBIDDEN);
    }

    public static TowerEntryException visitorKeyRequired() {
        return new TowerEntryException(
                VISITOR_KEY_REQUIRED,
                "error.tower.leave.visitor_key_required",
                HttpStatus.BAD_REQUEST);
    }
}
