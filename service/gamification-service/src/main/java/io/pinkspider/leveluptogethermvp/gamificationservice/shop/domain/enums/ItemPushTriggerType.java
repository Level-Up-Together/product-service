package io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.enums;

/**
 * LUT-528: 장착 아이템 푸시 대사(메시지)의 상태 태그. 발송 시점의 유저 상태에 맞춰 대사를 고르기 위한 구분값이며, 실제 상태 판정·선택 로직은 발송 로직
 * 티켓에서 정의한다. 이 티켓(데이터 모델)에서는 값만 도입하며 기존 메시지는 모두 {@link #ANY} 로 이관된다.
 */
public enum ItemPushTriggerType {
    /** 공통 — 상태 풀이 비었을 때 폴백으로 쓰는 범용 대사 */
    ANY,
    /** 전날 미션을 완료했고 오늘은 아직 활동 전 — 독려 */
    BEFORE_ACTIVITY,
    /** 오늘 미션을 1개 이상 완료 — 칭찬·교감 */
    AFTER_COMPLETE,
    /** 전날 미션 완료 0개 — 그리움·복귀 유도 */
    INACTIVE
}
