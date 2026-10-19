package io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.enums;

/**
 * LUT-573: 장착 아이템 푸시 발송 슬롯. 아이템 설정의 시각 2개(아침·오후) + 정오 고정 1개. 슬롯마다 발송 조건과 대사 풀(trigger_type)이 정해져
 * 있고, 조건에 안 맞으면 그 슬롯은 스킵한다(다른 상태 대사로 대체하지 않는다). 발송 로그에는 메시지의 trigger_type 이 아니라 <b>발송된 슬롯</b>을
 * 기록한다(ANY 폴백이어도).
 */
public enum ItemPushSlot {
    /** 아침 — 전날 활동 있음 AND 오늘 완료 0건. 시각은 아이템 설정 {@code before_activity_time}(08:00~11:59). */
    BEFORE_ACTIVITY(ItemPushTriggerType.BEFORE_ACTIVITY),
    /** 정오 12:00 고정 — 전날 활동 없음 AND 오늘 완료 0건 AND INACTIVE 백오프 발송일. 설정 시각이 하나라도 있어야 활성. */
    INACTIVE(ItemPushTriggerType.INACTIVE),
    /** 오후 — 오늘 완료 1건 이상. 시각은 아이템 설정 {@code after_complete_time}(12:00~20:59). */
    AFTER_COMPLETE(ItemPushTriggerType.AFTER_COMPLETE);

    /** 정오 슬롯 고정 시각 (유저 로컬) */
    public static final String NOON_TIME = "12:00";

    private final ItemPushTriggerType triggerType;

    ItemPushSlot(ItemPushTriggerType triggerType) {
        this.triggerType = triggerType;
    }

    /** 이 슬롯에서 우선 고르는 대사 풀의 trigger_type */
    public ItemPushTriggerType triggerType() {
        return triggerType;
    }
}
