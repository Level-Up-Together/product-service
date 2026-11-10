package io.pinkspider.leveluptogethermvp.towerservice.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/** LUT-760: 신고 결과 — {@code duplicate} = 같은 사람이 이미 신고해 세지 않음 · {@code hidden} = 지금 숨김 상태(3건이면 자동) */
@JsonNaming(SnakeCaseStrategy.class)
public record TowerNpcEntryReportResponse(Boolean duplicate, Boolean hidden) {}
