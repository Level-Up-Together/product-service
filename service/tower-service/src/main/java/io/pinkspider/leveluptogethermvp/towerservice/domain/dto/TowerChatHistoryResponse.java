package io.pinkspider.leveluptogethermvp.towerservice.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;

/** LUT-633: 최근 채팅(오래된 것부터, 최대 50건, 차단 유저 제외) — 로그 패널 초기 표시용 */
@JsonNaming(SnakeCaseStrategy.class)
public record TowerChatHistoryResponse(Long instanceId, List<TowerChatMessage> messages) {}
