package io.pinkspider.leveluptogethermvp.towerservice.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.LocalDateTime;

/**
 * LUT-633: 처리(금칙어 가림)된 채팅 1건 — 브로드캐스트 {@code CHAT} 이벤트와 최근 50건 조회에 같은 모양. 원문은 절대 싣지 않는다(운영 로그만). 보낸
 * 사람 표시는 {@code nickname}(로그인) 또는 {@code visitor_no}(방문객, 클라이언트가 locale 접두사로 조립).
 */
@JsonNaming(SnakeCaseStrategy.class)
public record TowerChatMessage(
        String id,
        Long instanceId,
        Long occupantId,
        String userId,
        Integer visitorNo,
        String nickname,
        String content,
        Boolean masked,
        LocalDateTime sentAt) {}
