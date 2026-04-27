package io.pinkspider.leveluptogethermvp.userservice.oauth.domain.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 회원가입 최종 완료 요청 (QA-108)
 * 닉네임 + 약관 동의를 한 번에 받아 처리한다.
 */
@Getter
@Setter
@NoArgsConstructor
@JsonNaming(SnakeCaseStrategy.class)
public class CompleteSignupRequestDto {

    @NotBlank(message = "닉네임은 필수입니다.")
    @Size(min = 2, max = 10, message = "닉네임은 2자 이상 10자 이하로 입력해주세요.")
    private String nickname;

    @NotNull(message = "약관 동의 정보는 필수입니다.")
    private List<TermAgreement> agreedTerms;

    private String deviceType;
    private String deviceId;

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonNaming(SnakeCaseStrategy.class)
    public static class TermAgreement {

        @NotNull
        private Long termVersionId;

        @JsonProperty("is_agreed")
        private boolean isAgreed;
    }
}
