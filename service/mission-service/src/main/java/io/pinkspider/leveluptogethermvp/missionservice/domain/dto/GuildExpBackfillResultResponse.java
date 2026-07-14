package io.pinkspider.leveluptogethermvp.missionservice.domain.dto;

/**
 * LUT-236: 자동종료 길드 경험치 소급 보정 결과.
 *
 * @param executionsScanned 조회한 대상 수행 기록 수
 * @param guildExpGranted    실제로 길드 경험치를 소급 지급한 수
 * @param totalExpGranted    소급 지급한 길드 경험치 총합
 * @param failed             지급 실패로 건너뛴 수 (재실행으로 보완 가능)
 */
public record GuildExpBackfillResultResponse(
    int executionsScanned,
    int guildExpGranted,
    long totalExpGranted,
    int failed) {}
