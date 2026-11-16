package io.pinkspider.leveluptogethermvp.statsservice.firstday.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.LocalDate;

/**
 * LUT-779: 첫날 리텐션 집계 1회 결과.
 *
 * @param dataStartDate 접속(DAU) 기록이 시작된 날 — 그 전 가입자는 대상이 아니다. 기록이 없으면 null(아무것도 안 함)
 * @param classified 이번에 그룹을 정한 유저 수
 * @param retentionUpdated 이번에 D1·D7·D30 중 하나라도 채운 유저 수
 */
@JsonNaming(SnakeCaseStrategy.class)
public record FirstDayRetentionRunResponse(
        LocalDate dataStartDate, int classified, int retentionUpdated) {}
