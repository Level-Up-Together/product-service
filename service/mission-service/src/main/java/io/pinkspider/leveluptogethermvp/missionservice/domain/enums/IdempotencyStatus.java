package io.pinkspider.leveluptogethermvp.missionservice.domain.enums;

/** LUT-577: 미션 생성 멱등 키 처리 상태 */
public enum IdempotencyStatus {
    /** 키를 선점했고 미션 생성이 진행 중 — 같은 키의 동시 요청은 409 */
    PROCESSING,
    /** 미션 생성 완료 — 같은 키 재요청은 저장된 응답을 그대로 돌려준다 */
    COMPLETED
}
