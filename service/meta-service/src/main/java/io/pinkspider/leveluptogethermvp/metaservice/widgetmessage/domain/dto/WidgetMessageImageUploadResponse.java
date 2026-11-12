package io.pinkspider.leveluptogethermvp.metaservice.widgetmessage.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * LUT-797: 위젯 문장 배경 이미지 업로드 결과. {@code url} 을 문장 작성·수정 요청의 {@code background_image_url} 에 넣는다.
 * {@code width}·{@code height} 는 어드민이 "최소 512×512" 경고에 쓴다(헤더를 못 읽으면 null).
 */
@JsonNaming(SnakeCaseStrategy.class)
public record WidgetMessageImageUploadResponse(
        String url, Integer width, Integer height, long sizeBytes) {}
