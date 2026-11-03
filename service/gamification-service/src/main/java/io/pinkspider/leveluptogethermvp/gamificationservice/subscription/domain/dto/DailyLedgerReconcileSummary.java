package io.pinkspider.leveluptogethermvp.gamificationservice.subscription.domain.dto;

/**
 * LUT-677: Android 원장 일일 정합 결과.
 *
 * @param targets 대상 구독 수(활성·최근 만료 Android)
 * @param synced Play 재조회·대조를 마친 수
 * @param skipped Play 재조회 실패 등으로 건너뛴 수(다음 날 다시 본다)
 * @param rowsWritten 새로 쓰거나 정정한 원장 행 수 — 0 이 정상, 양수면 RTDN 전달 경로 이상 신호
 */
public record DailyLedgerReconcileSummary(int targets, int synced, int skipped, int rowsWritten) {}
