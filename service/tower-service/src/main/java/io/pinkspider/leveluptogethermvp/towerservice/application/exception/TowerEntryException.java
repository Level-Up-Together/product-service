package io.pinkspider.leveluptogethermvp.towerservice.application.exception;

import org.springframework.http.HttpStatus;

/** LUT-630: 탑 입장·퇴장 거절. 서버가 최종 판정한다(프론트 가드만으로는 우회된다). */
public class TowerEntryException extends TowerException {

    public static final String FLOOR_NOT_AVAILABLE = "150201";
    public static final String LOGIN_REQUIRED = "150202";
    public static final String LEVEL_INSUFFICIENT = "150203";
    public static final String VISITOR_KEY_REQUIRED = "150204";

    private TowerEntryException(String code, String messageKey, HttpStatus status) {
        super(code, messageKey, status);
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

    public static TowerEntryException visitorKeyRequired() {
        return new TowerEntryException(
                VISITOR_KEY_REQUIRED,
                "error.tower.leave.visitor_key_required",
                HttpStatus.BAD_REQUEST);
    }
}
