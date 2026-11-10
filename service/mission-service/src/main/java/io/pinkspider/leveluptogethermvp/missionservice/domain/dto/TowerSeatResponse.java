package io.pinkspider.leveluptogethermvp.missionservice.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.global.facade.dto.TowerMissionSeatDto;

/** LUT-632: 미션 완료 응답의 탑 자리 — 앉아 있던 층·자동 착석 여부·받은 응원 수 (완료 화면 안내용). 탑에 앉지 않았던 수행은 응답에서 빠진다(null). */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record TowerSeatResponse(Integer floorNumber, Boolean autoSeated, Integer cheerCount) {

    public static TowerSeatResponse from(TowerMissionSeatDto dto) {
        return new TowerSeatResponse(dto.floorNumber(), dto.autoSeated(), dto.cheerCount());
    }
}
