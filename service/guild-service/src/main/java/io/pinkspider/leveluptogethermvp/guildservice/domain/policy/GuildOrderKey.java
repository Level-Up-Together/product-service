package io.pinkspider.leveluptogethermvp.guildservice.domain.policy;

/**
 * LUT-853: 길드 홈 목록 정렬에 쓰는 값만 모은 것. 추천 길드는 공개 길드의 이 값만 읽어 정렬하고({@link GuildHomeOrderPolicy}), 그 페이지에
 * 든 길드만 응답으로 조립한다 — 정렬한 뒤 페이지를 나눈다.
 */
public record GuildOrderKey(Long id, String masterId, Integer currentLevel, Integer totalPoint) {}
