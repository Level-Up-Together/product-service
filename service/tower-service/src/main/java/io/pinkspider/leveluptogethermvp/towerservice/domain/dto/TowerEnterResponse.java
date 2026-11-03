package io.pinkspider.leveluptogethermvp.towerservice.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * LUT-630: 입장 결과. {@code resumed} = 자리 유지 중이던 방(또는 자동 착석 자리)으로 복귀. {@code visitor_key} 는
 * 방문객(비로그인)에게만 — 퇴장 때 본인 확인용(세션·이름은 LUT-705). 좌표는 배경 원본 픽셀.
 */
@JsonNaming(SnakeCaseStrategy.class)
public record TowerEnterResponse(
        Integer floorNumber,
        Long instanceId,
        Long occupantId,
        Boolean resumed,
        Boolean missionActive,
        String visitorKey,
        Integer entryPointX,
        Integer entryPointY,
        Integer seatX,
        Integer seatY,
        Long population) {}
