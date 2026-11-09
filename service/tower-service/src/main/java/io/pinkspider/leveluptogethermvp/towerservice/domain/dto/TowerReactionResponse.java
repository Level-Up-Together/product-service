package io.pinkspider.leveluptogethermvp.towerservice.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/** LUT-633: 리액션 결과 — {@code cheer} 는 응원으로 집계됐는가, {@code cheer_count} 는 그 유저의 이번 미션 응원 누계(응원일 때만) */
@JsonNaming(SnakeCaseStrategy.class)
public record TowerReactionResponse(String emoji, boolean cheer, Integer cheerCount) {}
