package io.pinkspider.leveluptogethermvp.towerservice.domain.dto;

/** LUT-630: 미션 시작 시 자리 결과 — 이미 탑에 있었으면 그 자리, 아니면 자동 착석한 층·방 */
public record TowerSeatResult(Integer floorNumber, Long instanceId, boolean autoSeated) {}
