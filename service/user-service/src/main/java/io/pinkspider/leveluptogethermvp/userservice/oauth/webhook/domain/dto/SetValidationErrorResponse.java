package io.pinkspider.leveluptogethermvp.userservice.oauth.webhook.domain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * SET 검증 실패 응답 DTO
 *
 * 카카오 SSF 규격에 따른 에러 응답 형식입니다.
 */
public record SetValidationErrorResponse(
    /**
     * 에러 코드
     * - invalid_request: JWT 규격 불일치
     * - invalid_key: 공개키 복호화 실패
     * - invalid_issuer: Issuer 불일치
     * - invalid_audience: 앱ID 불일치
     */
    @JsonProperty("err")
    String errorCode,

    /**
     * 에러 설명
     */
    @JsonProperty("description")
    String description
) {
}
