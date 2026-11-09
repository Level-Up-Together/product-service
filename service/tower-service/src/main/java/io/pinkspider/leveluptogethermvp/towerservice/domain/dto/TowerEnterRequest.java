package io.pinkspider.leveluptogethermvp.towerservice.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.Size;

/**
 * LUT-705: 입장 요청 본문(선택, 비로그인만 쓴다). {@code visitor_key} = 이전 입장 응답의 방문객 키 — 같은 세션이면 같은 이름 번호로 들어오고 층
 * 이동은 대기열을 거치지 않는다. 서버가 모르는 키는 무시하고 새 세션을 준다. {@code device_id} = 기기 식별자(제재 단위). 구 클라이언트는 빈 본문을
 * 보낸다.
 */
@JsonNaming(SnakeCaseStrategy.class)
public record TowerEnterRequest(
        @Size(max = 64) String visitorKey, @Size(max = 128) String deviceId) {}
