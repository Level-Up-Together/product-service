package io.pinkspider.leveluptogethermvp.towerservice.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * LUT-635: 탑 공통 설정 — max_concurrent_users null = 제한 없음(부하 테스트 뒤 정한다, LUT-631/701). LUT-693:
 * npc_exposure_percent = NPC 노출 유저 비율(0~100, 기본 100).
 */
@JsonNaming(SnakeCaseStrategy.class)
public record TowerSettingsResponse(Integer maxConcurrentUsers, Integer npcExposurePercent) {}
