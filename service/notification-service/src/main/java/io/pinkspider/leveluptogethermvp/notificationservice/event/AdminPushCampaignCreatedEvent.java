package io.pinkspider.leveluptogethermvp.notificationservice.event;

/** LUT-508: 관리자 푸시 캠페인 이력 행 생성 — 커밋 후 비동기 발송 트리거 */
public record AdminPushCampaignCreatedEvent(Long campaignId) {}
