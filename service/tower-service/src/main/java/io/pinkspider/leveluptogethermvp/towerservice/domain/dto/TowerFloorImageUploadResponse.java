package io.pinkspider.leveluptogethermvp.towerservice.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.towerservice.domain.enums.TowerImageKind;

/**
 * LUT-635: 층 이미지 업로드 응답 — url 을 층 등록/수정의 background_image_url·floor_mask_url 에 그대로 쓴다. 크기는 읽을 수 있을
 * 때만(webp 는 null).
 */
@JsonNaming(SnakeCaseStrategy.class)
public record TowerFloorImageUploadResponse(
        TowerImageKind kind, String url, Integer width, Integer height) {}
