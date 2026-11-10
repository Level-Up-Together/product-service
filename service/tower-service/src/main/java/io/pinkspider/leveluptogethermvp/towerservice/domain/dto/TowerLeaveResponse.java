package io.pinkspider.leveluptogethermvp.towerservice.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * LUT-630: 퇴장 결과(멱등). {@code left} = 자리를 비웠다, {@code seat_held} = 미션 중이라 자리를 남겨 두었다(돌아오면 같은 방으로
 * 복귀). 둘 다 false 면 탑에 없었다.
 */
@JsonNaming(SnakeCaseStrategy.class)
public record TowerLeaveResponse(Boolean left, Boolean seatHeld) {}
