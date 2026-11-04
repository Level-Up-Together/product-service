package io.pinkspider.leveluptogethermvp.towerservice.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * LUT-637: 배경 음악 업로드 응답 — url 을 층 등록/수정의 bgm_url 에 그대로 쓴다. 길이(초)는 서버가 디코딩하지 않으므로 어드민이 {@code
 * <audio>} 메타데이터로 읽어 1분 30초~2분 권장을 안내한다.
 */
@JsonNaming(SnakeCaseStrategy.class)
public record TowerFloorAudioUploadResponse(String url, long sizeBytes, String contentType) {}
