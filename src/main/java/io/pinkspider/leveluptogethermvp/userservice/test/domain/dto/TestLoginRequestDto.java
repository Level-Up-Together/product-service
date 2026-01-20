package io.pinkspider.leveluptogethermvp.userservice.test.domain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 테스트 로그인 요청 DTO
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestLoginRequestDto {

    /**
     * 테스트 사용자 ID (선택)
     * 지정하지 않으면 자동 생성됩니다.
     */
    @JsonProperty("test_user_id")
    private String testUserId;

    /**
     * 이메일 (필수)
     */
    @NotBlank(message = "이메일은 필수입니다")
    @Email(message = "올바른 이메일 형식이 아닙니다")
    @JsonProperty("email")
    private String email;

    /**
     * 닉네임 (선택)
     * 지정하지 않으면 이메일 앞부분을 사용합니다.
     */
    @JsonProperty("nickname")
    private String nickname;

    /**
     * 디바이스 타입 (선택, 기본값: web)
     */
    @JsonProperty("device_type")
    private String deviceType;

    /**
     * 디바이스 ID (선택)
     * 지정하지 않으면 자동 생성됩니다.
     */
    @JsonProperty("device_id")
    private String deviceId;
}
