package io.pinkspider.leveluptogethermvp.towerservice.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * LUT-635: 탑 공통 설정 수정. max_concurrent_users 가 null 이면 제한 없음(행 삭제).
 *
 * <p>LUT-693: npc_exposure_percent(0~100) = NPC 를 보여 줄 유저 비율(A/B). null 이면 바꾸지 않는다 — 이 필드를 모르는 구
 * 패스스루가 보낸 요청이 값을 지우지 않게.
 */
@JsonNaming(SnakeCaseStrategy.class)
public record TowerSettingsRequest(
        @Min(1) Integer maxConcurrentUsers, @Min(0) @Max(100) Integer npcExposurePercent) {}
