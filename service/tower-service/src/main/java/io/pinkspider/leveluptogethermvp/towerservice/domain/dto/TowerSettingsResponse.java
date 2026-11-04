package io.pinkspider.leveluptogethermvp.towerservice.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/** LUT-635: 탑 공통 설정 — max_concurrent_users null = 제한 없음(부하 테스트 뒤 정한다, LUT-631/701). */
@JsonNaming(SnakeCaseStrategy.class)
public record TowerSettingsResponse(Integer maxConcurrentUsers) {}
