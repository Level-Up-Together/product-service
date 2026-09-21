package io.pinkspider.leveluptogethermvp.notificationservice.domain.enums;

/** LUT-508: 관리자 푸시 발송 대상 유형 */
public enum AdminPushTargetType {
    /** 활성(ACTIVE) 유저 전체 */
    ALL,
    /** 요청에 실린 유저 ID 목록 (활성 유저만 남긴다) */
    USERS
}
