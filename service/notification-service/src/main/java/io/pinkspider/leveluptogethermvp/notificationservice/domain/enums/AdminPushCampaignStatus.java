package io.pinkspider.leveluptogethermvp.notificationservice.domain.enums;

/** LUT-508: 관리자 푸시 캠페인 상태 — 이력 행 생성(PENDING) → 비동기 발송(SENDING) → 완료/실패 */
public enum AdminPushCampaignStatus {
    PENDING,
    SENDING,
    COMPLETED,
    FAILED
}
