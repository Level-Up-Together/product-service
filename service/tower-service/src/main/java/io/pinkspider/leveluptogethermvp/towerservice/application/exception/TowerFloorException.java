package io.pinkspider.leveluptogethermvp.towerservice.application.exception;

import org.springframework.http.HttpStatus;

/**
 * LUT-629: 탑 층 저장·조회 오류. 어드민 입력 오류(4xx)라 플랫폼 {@code RestExceptionHandler} 의 500 대신 {@code
 * TowerExceptionHandler} 가 상태 코드와 함께 응답한다. 코드 앞자리 15 = tower-service.
 */
public class TowerFloorException extends TowerException {

    public static final String NOT_FOUND = "150101";
    public static final String DUPLICATE_NUMBER = "150102";
    public static final String ENTRY_LEVEL_ORDER = "150103";
    public static final String FLOOR1_ENTRY_LEVEL = "150104";
    public static final String GUEST_REQUIRES_NO_LEVEL = "150105";
    public static final String GUEST_NOT_CONTIGUOUS = "150106";
    public static final String PUBLIC_NOT_CONTIGUOUS = "150107";
    public static final String ENTRY_POINT_OUTSIDE_MASK = "150108";
    public static final String MASK_UNREADABLE = "150109";

    /** LUT-635: 업로드 파일 없음·크기 초과·확장자 불허 */
    public static final String IMAGE_INVALID = "150110";

    /** LUT-635: 저장소(디스크·S3) 쓰기 실패 — 서버 오류 */
    public static final String IMAGE_SAVE_FAILED = "150111";

    /** LUT-635: 마스크는 PNG 만 (픽셀을 그대로 읽는다) */
    public static final String MASK_NOT_PNG = "150112";

    private TowerFloorException(String code, String messageKey, HttpStatus status) {
        super(code, messageKey, status);
    }

    public static TowerFloorException notFound() {
        return new TowerFloorException(
                NOT_FOUND, "error.tower.floor.not_found", HttpStatus.NOT_FOUND);
    }

    public static TowerFloorException duplicateNumber() {
        return bad(DUPLICATE_NUMBER, "error.tower.floor.duplicate_number");
    }

    public static TowerFloorException entryLevelOrder() {
        return bad(ENTRY_LEVEL_ORDER, "error.tower.floor.entry_level_order");
    }

    public static TowerFloorException floor1EntryLevel() {
        return bad(FLOOR1_ENTRY_LEVEL, "error.tower.floor.floor1_entry_level");
    }

    public static TowerFloorException guestRequiresNoLevel() {
        return bad(GUEST_REQUIRES_NO_LEVEL, "error.tower.floor.guest_requires_no_level");
    }

    public static TowerFloorException guestNotContiguous() {
        return bad(GUEST_NOT_CONTIGUOUS, "error.tower.floor.guest_not_contiguous");
    }

    public static TowerFloorException publicNotContiguous() {
        return bad(PUBLIC_NOT_CONTIGUOUS, "error.tower.floor.public_not_contiguous");
    }

    public static TowerFloorException entryPointOutsideMask() {
        return bad(ENTRY_POINT_OUTSIDE_MASK, "error.tower.floor.entry_point_outside_mask");
    }

    public static TowerFloorException maskUnreadable() {
        return bad(MASK_UNREADABLE, "error.tower.floor.mask_unreadable");
    }

    public static TowerFloorException imageInvalid() {
        return bad(IMAGE_INVALID, "error.tower.floor.image_invalid");
    }

    public static TowerFloorException imageSaveFailed() {
        return new TowerFloorException(
                IMAGE_SAVE_FAILED,
                "error.tower.floor.image_save_failed",
                HttpStatus.INTERNAL_SERVER_ERROR);
    }

    public static TowerFloorException maskNotPng() {
        return bad(MASK_NOT_PNG, "error.tower.floor.mask_not_png");
    }

    private static TowerFloorException bad(String code, String key) {
        return new TowerFloorException(code, key, HttpStatus.BAD_REQUEST);
    }
}
