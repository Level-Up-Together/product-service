package io.pinkspider.leveluptogethermvp.towerservice.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;

/** LUT-760: 공개 조회 — 덜 보여 준 글부터 최대 50개 + 공개 글 전체 수. 공개 방식이 NONE 이거나 꺼졌으면 빈 목록 */
@JsonNaming(SnakeCaseStrategy.class)
public record TowerNpcEntryRandomResponse(List<TowerNpcEntryView> entries, Long total) {}
