package io.pinkspider.leveluptogethermvp.towerservice.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.Min;

/** LUT-635: 탑 공통 설정 수정. max_concurrent_users 가 null 이면 제한 없음(행 삭제). */
@JsonNaming(SnakeCaseStrategy.class)
public record TowerSettingsRequest(@Min(1) Integer maxConcurrentUsers) {}
