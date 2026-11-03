package io.pinkspider.leveluptogethermvp.towerservice.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;

/**
 * LUT-629: 유저용 층 목록. {@code defaultFloorNumber} = 마지막으로 머문 층(입장 가능하면), 아니면 입장 가능한 가장 낮은 층, 없으면
 * null. 공개 층이 없으면 빈 목록(디폴트 층은 두지 않는다).
 */
@JsonNaming(SnakeCaseStrategy.class)
public record TowerFloorListResponse(List<TowerFloorResponse> floors, Integer defaultFloorNumber) {}
