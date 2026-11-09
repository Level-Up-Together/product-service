package io.pinkspider.leveluptogethermvp.towerservice.application.exception;

import org.springframework.http.HttpStatus;

/**
 * LUT-633: 탑 채팅·리액션 거절. 코드 가운데 두 자리 03 = 채팅. 프론트가 거부 사유 문구를 띄울 수 있게 코드로 가른다 — 서버가 최종 판정(화면 가드만으로는
 * 우회된다).
 */
public class TowerChatException extends TowerException {
    public static final String NOT_IN_INSTANCE = "150301";
    public static final String RATE_LIMITED = "150302";
    public static final String DUPLICATE = "150303";

    /** LUT-768 부터 쓰지 않는다(미션 중 채팅 허용). 번호는 예약 — 다른 뜻으로 재사용하지 않는다 */
    public static final String MISSION_IN_PROGRESS = "150304";

    public static final String PROFANITY_REJECTED = "150305";
    public static final String MUTED = "150306";
    public static final String CONTENT_INVALID = "150307";
    public static final String VISITOR_CONTENT_BLOCKED = "150308";
    public static final String INVALID_REACTION = "150309";

    private TowerChatException(String code, String messageKey, HttpStatus status) {
        super(code, messageKey, status);
    }

    public static TowerChatException notInInstance() {
        return new TowerChatException(
                NOT_IN_INSTANCE, "error.tower.chat.not_in_instance", HttpStatus.FORBIDDEN);
    }

    public static TowerChatException rateLimited() {
        return new TowerChatException(
                RATE_LIMITED, "error.tower.chat.rate_limited", HttpStatus.TOO_MANY_REQUESTS);
    }

    public static TowerChatException duplicate() {
        return new TowerChatException(
                DUPLICATE, "error.tower.chat.duplicate", HttpStatus.BAD_REQUEST);
    }

    public static TowerChatException profanityRejected() {
        return new TowerChatException(
                PROFANITY_REJECTED, "error.tower.chat.profanity_rejected", HttpStatus.BAD_REQUEST);
    }

    public static TowerChatException muted() {
        return new TowerChatException(MUTED, "error.tower.chat.muted", HttpStatus.FORBIDDEN);
    }

    public static TowerChatException contentInvalid() {
        return new TowerChatException(
                CONTENT_INVALID, "error.tower.chat.content_invalid", HttpStatus.BAD_REQUEST);
    }

    public static TowerChatException visitorContentBlocked() {
        return new TowerChatException(
                VISITOR_CONTENT_BLOCKED,
                "error.tower.chat.visitor_content_blocked",
                HttpStatus.BAD_REQUEST);
    }

    public static TowerChatException invalidReaction() {
        return new TowerChatException(
                INVALID_REACTION, "error.tower.chat.invalid_reaction", HttpStatus.BAD_REQUEST);
    }
}
