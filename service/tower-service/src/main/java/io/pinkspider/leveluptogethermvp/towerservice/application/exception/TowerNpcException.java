package io.pinkspider.leveluptogethermvp.towerservice.application.exception;

import org.springframework.http.HttpStatus;

/** LUT-693: 탑 NPC 저장 오류(어드민 입력, 4xx). 코드 1504xx. 층이 없으면 150101, 마스크를 못 읽으면 150109(층 코드 재사용). */
public class TowerNpcException extends TowerException {

    public static final String NOT_FOUND = "150401";
    public static final String LIMIT_EXCEEDED = "150402";

    /** LUT-746: 배경 그림 밖 (예전엔 바닥 마스크 밖 — 코드는 그대로) */
    public static final String SEAT_OUTSIDE_BACKGROUND = "150403";

    public static final String LINE_TOO_LONG = "150404";
    public static final String CARD_LINK_TARGET_MISSING = "150405";
    public static final String CARD_LINK_INVALID = "150406";
    public static final String CARD_PERIOD_INVALID = "150407";

    private TowerNpcException(String code, String messageKey, HttpStatus status) {
        super(code, messageKey, status);
    }

    public static TowerNpcException notFound() {
        return new TowerNpcException(NOT_FOUND, "error.tower.npc.not_found", HttpStatus.NOT_FOUND);
    }

    /** 층당 최대 3명 */
    public static TowerNpcException limitExceeded() {
        return bad(LIMIT_EXCEEDED, "error.tower.npc.limit_exceeded");
    }

    public static TowerNpcException seatOutsideBackground() {
        return bad(SEAT_OUTSIDE_BACKGROUND, "error.tower.npc.seat_outside_background");
    }

    /** 대사 한 문장이 2줄(한글 약 40자)을 넘는다 */
    public static TowerNpcException lineTooLong() {
        return bad(LINE_TOO_LONG, "error.tower.npc.line_too_long");
    }

    /** LUT-733: 링크 대상 상점 아이템(판매 중)·이벤트가 없다 */
    public static TowerNpcException cardLinkTargetMissing() {
        return bad(CARD_LINK_TARGET_MISSING, "error.tower.npc.card_link_target_missing");
    }

    /** LUT-733: 링크 형식 오류 — 대상 ID 없음, 앱 경로가 아님(/ 로 시작, // · :// · 공백 · 역슬래시 금지) */
    public static TowerNpcException cardLinkInvalid() {
        return bad(CARD_LINK_INVALID, "error.tower.npc.card_link_invalid");
    }

    /** LUT-733: 노출 시작일이 종료일보다 늦다 */
    public static TowerNpcException cardPeriodInvalid() {
        return bad(CARD_PERIOD_INVALID, "error.tower.npc.card_period_invalid");
    }

    private static TowerNpcException bad(String code, String key) {
        return new TowerNpcException(code, key, HttpStatus.BAD_REQUEST);
    }
}
