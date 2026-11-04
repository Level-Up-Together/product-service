package io.pinkspider.leveluptogethermvp.towerservice.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/** LUT-705: 신고 접수 결과 — {@code duplicate} = 24시간 안에 같은 방문객을 이미 신고해 새로 남기지 않았다 */
@JsonNaming(SnakeCaseStrategy.class)
public record TowerVisitorReportResponse(Long reportId, Boolean duplicate) {}
