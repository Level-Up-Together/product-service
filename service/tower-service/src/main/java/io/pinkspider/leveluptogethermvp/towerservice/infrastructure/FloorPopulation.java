package io.pinkspider.leveluptogethermvp.towerservice.infrastructure;

/** LUT-630: 층 전체 인원(모든 인스턴스 합, 방문객·자리 유지·자동 착석 포함, NPC 제외) */
public record FloorPopulation(Integer floorNumber, Long total) {}
