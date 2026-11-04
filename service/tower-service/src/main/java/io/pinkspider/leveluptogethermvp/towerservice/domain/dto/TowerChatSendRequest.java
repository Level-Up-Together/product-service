package io.pinkspider.leveluptogethermvp.towerservice.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** LUT-633: 채팅 전송 — 방문객은 입장 때 받은 {@code visitor_key}. 길이 상한(로그인 200·방문객 40)은 서비스가 150307 로 판정 */
@JsonNaming(SnakeCaseStrategy.class)
public record TowerChatSendRequest(
        @NotBlank @Size(max = 1000) String content, @Size(max = 64) String visitorKey) {}
