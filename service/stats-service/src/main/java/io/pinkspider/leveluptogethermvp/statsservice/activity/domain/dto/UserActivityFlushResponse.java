package io.pinkspider.leveluptogethermvp.statsservice.activity.domain.dto;

import java.time.LocalDate;

/** LUT-598: 수동 flush 결과 — 적재한 날짜, 이번에 넣거나 갱신한 행 수, 그날 활동 유저 수(적재 결과) */
public record UserActivityFlushResponse(LocalDate date, int flushedRows, long activeUsers) {}
