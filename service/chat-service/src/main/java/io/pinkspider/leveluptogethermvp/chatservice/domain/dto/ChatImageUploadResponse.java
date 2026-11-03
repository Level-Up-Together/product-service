package io.pinkspider.leveluptogethermvp.chatservice.domain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 채팅·DM 이미지 업로드 결과 (LUT-590). 클라이언트는 이 URL 을 기존 전송 API(WS/REST)의 {@code image_url} 에 실어 보낸다. 썸네일은
 * 원본 URL 에 {@code _thumb} 접미사를 붙인 변형(없으면 원본 폴백).
 */
public record ChatImageUploadResponse(@JsonProperty("image_url") String imageUrl) {}
