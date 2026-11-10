package io.pinkspider.leveluptogethermvp.towerservice.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** LUT-705: 방문객 신고 — 같은 방에 있는 방문객 점유자(아바타·채팅의 {@code occupant_id}) */
@JsonNaming(SnakeCaseStrategy.class)
public record TowerVisitorReportRequest(@NotNull Long occupantId, @Size(max = 500) String reason) {}
