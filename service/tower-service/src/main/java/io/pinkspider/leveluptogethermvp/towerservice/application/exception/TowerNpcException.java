package io.pinkspider.leveluptogethermvp.towerservice.application.exception;

import org.springframework.http.HttpStatus;

/** LUT-693: 탑 NPC 저장 오류(어드민 입력, 4xx). 코드 1504xx. 층이 없으면 150101, 마스크를 못 읽으면 150109(층 코드 재사용). */
public class TowerNpcException extends TowerException {

    public static final String NOT_FOUND = "150401";
    public static final String LIMIT_EXCEEDED = "150402";
    public static final String SEAT_OUTSIDE_MASK = "150403";
    public static final String LINE_TOO_LONG = "150404";

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

    public static TowerNpcException seatOutsideMask() {
        return bad(SEAT_OUTSIDE_MASK, "error.tower.npc.seat_outside_mask");
    }

    /** 대사 한 문장이 2줄(한글 약 40자)을 넘는다 */
    public static TowerNpcException lineTooLong() {
        return bad(LINE_TOO_LONG, "error.tower.npc.line_too_long");
    }

    private static TowerNpcException bad(String code, String key) {
        return new TowerNpcException(code, key, HttpStatus.BAD_REQUEST);
    }
}
