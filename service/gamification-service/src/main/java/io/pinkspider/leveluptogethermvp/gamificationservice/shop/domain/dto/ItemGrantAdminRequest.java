package io.pinkspider.leveluptogethermvp.gamificationservice.shop.domain.dto;

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
public class ItemGrantAdminRequest {

    @NotBlank(message = "사용자 ID는 필수입니다.")
    private String userId;

    @NotNull(message = "아이템 ID는 필수입니다.")
    private Long shopItemId;

    @Size(max = 255, message = "사유는 255자 이내여야 합니다.")
    private String reason;
}
