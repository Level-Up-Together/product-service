package io.pinkspider.leveluptogethermvp.towerservice.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.Size;

/**
 * LUT-760: NPC 에 글 남기기. 본문 길이는 NPC 설정 {@code max_length} 로 서비스가 판정(150412), 이메일·동의는 {@code
 * email_enabled} 일 때만 본다(150413). 방문객은 {@code visitor_key}(입장 응답) 필수.
 */
@JsonNaming(SnakeCaseStrategy.class)
public record TowerNpcEntryRequest(
        @Size(max = 1000) String body,
        @Size(max = 254) String email,
        Boolean consent,
        @Size(max = 64) String visitorKey) {}
