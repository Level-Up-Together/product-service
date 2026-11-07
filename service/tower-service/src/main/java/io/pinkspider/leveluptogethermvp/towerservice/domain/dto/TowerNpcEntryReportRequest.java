package io.pinkspider.leveluptogethermvp.towerservice.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.Size;

/** LUT-760: 글 신고 — 방문객은 {@code visitor_key} 로 본인 확인(없으면 150204) */
@JsonNaming(SnakeCaseStrategy.class)
public record TowerNpcEntryReportRequest(@Size(max = 64) String visitorKey) {}
