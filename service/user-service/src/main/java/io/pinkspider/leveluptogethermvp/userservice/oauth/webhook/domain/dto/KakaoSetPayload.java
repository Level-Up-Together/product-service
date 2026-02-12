package io.pinkspider.leveluptogethermvp.userservice.oauth.webhook.domain.dto;

import io.pinkspider.leveluptogethermvp.userservice.oauth.webhook.domain.enums.KakaoAccountEventType;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 카카오 SET(Security Event Token) 페이로드 DTO
 *
 * SSF(Shared Signals and Events Framework) 규격에 따른 보안 이벤트 토큰 페이로드입니다.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KakaoSetPayload {

    /**
     * 사용자의 카카오 회원번호
     */
    private String sub;

    /**
     * SET 발급 시간 (Unix timestamp)
     */
    private long iat;

    /**
     * 이벤트 발생 시각 (Unix timestamp)
     */
    private long toe;

    /**
     * SET 고유 식별값
     */
    private String jti;

    /**
     * 이벤트 타입
     */
    private KakaoAccountEventType eventType;

    /**
     * 이벤트 상세 데이터
     */
    private Map<String, Object> eventData;
}
