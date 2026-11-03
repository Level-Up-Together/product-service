package io.pinkspider.leveluptogethermvp.towerservice.infrastructure;

/**
 * LUT-630: 인스턴스별 인원. {@code counted} = 정원에 세는 인원(직접 입장·방문객, 자리 유지 중 포함), {@code total} = 자동 착석자까지
 * 전부.
 */
public record InstanceLoad(Long instanceId, Long counted, Long total) {}
