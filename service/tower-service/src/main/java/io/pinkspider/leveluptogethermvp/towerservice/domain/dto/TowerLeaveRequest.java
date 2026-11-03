package io.pinkspider.leveluptogethermvp.towerservice.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.Size;

/** LUT-630: 퇴장 — 로그인 유저는 토큰으로, 방문객은 입장 때 받은 {@code visitor_key} 로 본인을 댄다 */
@JsonNaming(SnakeCaseStrategy.class)
public record TowerLeaveRequest(@Size(max = 64) String visitorKey) {}
