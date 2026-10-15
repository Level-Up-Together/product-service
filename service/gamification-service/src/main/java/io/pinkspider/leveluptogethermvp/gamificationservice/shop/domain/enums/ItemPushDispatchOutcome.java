package io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.enums;

/**
 * LUT-541: 유저 1명에 대한 장착 아이템 푸시 시도 결과.
 *
 * <p>왜 필요한가 — 기존에는 발송 경로가 모든 스킵을 {@code return} 으로 조용히 끝내서, QA·운영이 "왜 안 왔는지"를 코드를 읽지 않고는 알 수 없었다
 * (LUT-541 조사에서 가설이 두 번 재정의된 비용). 사유를 값으로 돌려주면 스케줄러가 집계 로그를 남길 수 있고, 테스트도 깨지기 쉬운 로그 문자열 대신 결과값으로
 * 단언할 수 있다.
 */
public enum ItemPushDispatchOutcome {
    /** 발송 선점 성공 — 커밋 후 알림 리스너가 실제 푸시를 보낸다. */
    SENT,

    /** 유저·로컬날짜당 1건 제약에 걸림 (오늘 이미 받음 — 장착을 바꿔도 재발송 없음). */
    SKIP_ALREADY_SENT,

    /** INACTIVE 백오프 — 연속 미완료 일수 k 가 발송 일차(1·3·7·14)가 아님. */
    SKIP_BACKOFF,

    /** 상태에 맞는 trigger_type 대사와 ANY 대사가 모두 없음 (어드민 대사 미등록). */
    SKIP_NO_MESSAGE,

    /** 다른 인스턴스가 먼저 선점 (send_log 유니크 위반). */
    SKIP_RACE,
}
