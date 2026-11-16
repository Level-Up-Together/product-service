package io.pinkspider.leveluptogethermvp.statsservice.firstday.domain.enums;

/** LUT-779: 첫날 행동별 D7 리텐션 판정 결과 (어드민 결과 칩, LUT-780) */
public enum RetentionVerdictCode {
    /** 혼자 완료한 유저가 미완료 유저보다 5%p 이상 더 남고 유의함 */
    SOLO_RETAINS,
    /** 차이가 5%p 미만이거나 유의하지 않음 */
    SOLO_WEAK,
    /** 함께 완료한 유저가 혼자 완료한 유저보다 5%p 이상 더 남고 유의함 */
    SOCIAL_BOOSTS,
    /** 함께 완료한 유저가 5%p 이상 덜 남고 유의함 */
    SOCIAL_HURTS,
    /** 그 외 */
    SOCIAL_NO_DIFF,
    /** 비교하는 두 그룹 중 하나라도 D7 판정 가능 인원이 30명 미만 */
    NOT_ENOUGH_DATA
}
