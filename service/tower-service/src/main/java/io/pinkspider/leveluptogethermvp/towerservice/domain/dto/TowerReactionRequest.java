package io.pinkspider.leveluptogethermvp.towerservice.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * LUT-633: 리액션 — 서버 프리셋 이모지만. {@code target_user_id} 를 주면 그 유저의 완료 연출 응원 창(10초) 안일 때 응원으로 센다(보상
 * 없음).
 */
@JsonNaming(SnakeCaseStrategy.class)
public record TowerReactionRequest(
        @NotBlank @Size(max = 16) String emoji,
        @Size(max = 255) String targetUserId,
        @Size(max = 64) String visitorKey) {}
