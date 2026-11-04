package io.pinkspider.leveluptogethermvp.towerservice.application.exception;

import org.springframework.http.HttpStatus;

/**
 * LUT-705: 탑 방문객(비로그인) 제한·제재·신고 오류. 코드 1505xx. 입장 금지·채팅 금지 기기는 403, IP 빈도 초과는 429, 신고 대상이 그 방의 방문객이
 * 아니면 404.
 */
public class TowerVisitorException extends TowerException {

    public static final String ENTRY_BANNED = "150501";
    public static final String CHAT_BANNED = "150502";
    public static final String RATE_LIMITED = "150503";
    public static final String REPORT_TARGET_NOT_FOUND = "150504";
    public static final String SANCTION_INVALID = "150505";
    public static final String SANCTION_NOT_FOUND = "150506";

    private TowerVisitorException(String code, String messageKey, HttpStatus status) {
        super(code, messageKey, status);
    }

    public static TowerVisitorException entryBanned() {
        return new TowerVisitorException(
                ENTRY_BANNED, "error.tower.visitor.entry_banned", HttpStatus.FORBIDDEN);
    }

    public static TowerVisitorException chatBanned() {
        return new TowerVisitorException(
                CHAT_BANNED, "error.tower.visitor.chat_banned", HttpStatus.FORBIDDEN);
    }

    /** 같은 IP 에서 방문객 입장이 너무 잦거나 방문객 세션이 너무 많다 */
    public static TowerVisitorException rateLimited() {
        return new TowerVisitorException(
                RATE_LIMITED, "error.tower.visitor.rate_limited", HttpStatus.TOO_MANY_REQUESTS);
    }

    public static TowerVisitorException reportTargetNotFound() {
        return new TowerVisitorException(
                REPORT_TARGET_NOT_FOUND,
                "error.tower.visitor.report_target_not_found",
                HttpStatus.NOT_FOUND);
    }

    public static TowerVisitorException sanctionInvalid() {
        return new TowerVisitorException(
                SANCTION_INVALID, "error.tower.visitor.sanction_invalid", HttpStatus.BAD_REQUEST);
    }

    public static TowerVisitorException sanctionNotFound() {
        return new TowerVisitorException(
                SANCTION_NOT_FOUND, "error.tower.visitor.sanction_not_found", HttpStatus.NOT_FOUND);
    }
}
