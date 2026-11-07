package io.pinkspider.leveluptogethermvp.towerservice.application.exception;

import org.springframework.http.HttpStatus;

/**
 * LUT-693: 탑 NPC 저장 오류(어드민 입력, 4xx). 코드 1504xx. 층이 없으면 150101, 마스크를 못 읽으면 150109(층 코드 재사용).
 * LUT-760: 입력 받기 설정(150408~150409)·글 제출·조회·신고(150410~150415).
 */
public class TowerNpcException extends TowerException {

    public static final String NOT_FOUND = "150401";
    public static final String LIMIT_EXCEEDED = "150402";

    /** LUT-746: 배경 그림 밖 (예전엔 바닥 마스크 밖 — 코드는 그대로) */
    public static final String SEAT_OUTSIDE_BACKGROUND = "150403";

    public static final String LINE_TOO_LONG = "150404";
    public static final String CARD_LINK_TARGET_MISSING = "150405";
    public static final String CARD_LINK_INVALID = "150406";
    public static final String CARD_PERIOD_INVALID = "150407";

    /** LUT-760: 입력 받기 설정 오류 — 버튼 문구 4개 언어·이메일 동의 문구·공개 방식의 마스크 */
    public static final String FORM_INVALID = "150408";

    /** LUT-760: 공개 영역 마스크가 배경과 비율이 다르다 */
    public static final String FORM_MASK_ASPECT_MISMATCH = "150409";

    /** LUT-760: 이 NPC 는 입력을 받지 않는다(설정 없음·꺼짐) */
    public static final String FORM_DISABLED = "150410";

    /** LUT-760: 로그인 유저만 받는 NPC 에 방문객이 제출 */
    public static final String ENTRY_LOGIN_REQUIRED = "150411";

    /** LUT-760: 본문이 비었거나 최대 글자 수 초과 */
    public static final String ENTRY_BODY_INVALID = "150412";

    /** LUT-760: 이메일 형식이 틀리거나 동의하지 않음 */
    public static final String ENTRY_EMAIL_INVALID = "150413";

    /** LUT-760: 같은 NPC 에 하루 1회 초과 */
    public static final String ENTRY_DAILY_LIMIT = "150414";

    /** LUT-760: 글 없음 */
    public static final String ENTRY_NOT_FOUND = "150415";

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

    /** LUT-760 */
    public static TowerNpcException formInvalid() {
        return bad(FORM_INVALID, "error.tower.npc.form_invalid");
    }

    /** LUT-760 */
    public static TowerNpcException formMaskAspectMismatch() {
        return bad(FORM_MASK_ASPECT_MISMATCH, "error.tower.npc.form_mask_aspect_mismatch");
    }

    /** LUT-760 */
    public static TowerNpcException formDisabled() {
        return bad(FORM_DISABLED, "error.tower.npc.form_disabled");
    }

    /** LUT-760: 화면에서 막아도 서버가 다시 막는다 */
    public static TowerNpcException entryLoginRequired() {
        return new TowerNpcException(
                ENTRY_LOGIN_REQUIRED, "error.tower.npc.entry_login_required", HttpStatus.FORBIDDEN);
    }

    /** LUT-760 */
    public static TowerNpcException entryBodyInvalid() {
        return bad(ENTRY_BODY_INVALID, "error.tower.npc.entry_body_invalid");
    }

    /** LUT-760 */
    public static TowerNpcException entryEmailInvalid() {
        return bad(ENTRY_EMAIL_INVALID, "error.tower.npc.entry_email_invalid");
    }

    /** LUT-760: 로그인 유저는 user_id, 방문객은 visitor_key + IP 기준 */
    public static TowerNpcException entryDailyLimit() {
        return new TowerNpcException(
                ENTRY_DAILY_LIMIT,
                "error.tower.npc.entry_daily_limit",
                HttpStatus.TOO_MANY_REQUESTS);
    }

    /** LUT-760 */
    public static TowerNpcException entryNotFound() {
        return new TowerNpcException(
                ENTRY_NOT_FOUND, "error.tower.npc.entry_not_found", HttpStatus.NOT_FOUND);
    }

    private static TowerNpcException bad(String code, String key) {
        return new TowerNpcException(code, key, HttpStatus.BAD_REQUEST);
    }
}
