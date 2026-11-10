package io.pinkspider.leveluptogethermvp.towerservice.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerPixelImage;

/** LUT-727: 픽셀 배경 한 장 — 인덱스 PNG(64색 기본) 주소·실제 크기·버전(내용 해시, 프론트 효과 맵 대조용) */
@JsonNaming(SnakeCaseStrategy.class)
public record TowerPixelBackgroundDto(String url, Integer width, Integer height, String version) {

    public static TowerPixelBackgroundDto from(TowerPixelImage image) {
        if (image == null || image.getUrl() == null) {
            return null;
        }
        return new TowerPixelBackgroundDto(
                image.getUrl(), image.getWidth(), image.getHeight(), image.getVersion());
    }
}
