package io.pinkspider.leveluptogethermvp.gamificationservice.achievement.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(SnakeCaseStrategy.class)
public class TitleGrantAdminRequest {

    @NotBlank(message = "사용자 ID는 필수입니다.")
    private String userId;

    @NotNull(message = "칭호 ID는 필수입니다.")
    private Long titleId;

    @Size(max = 500, message = "부여 사유는 500자 이하이어야 합니다.")
    private String reason;
}