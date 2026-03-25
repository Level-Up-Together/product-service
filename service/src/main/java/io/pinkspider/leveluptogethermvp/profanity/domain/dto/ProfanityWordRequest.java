package io.pinkspider.leveluptogethermvp.profanity.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.profanity.domain.enums.ProfanityCategory;
import io.pinkspider.leveluptogethermvp.profanity.domain.enums.ProfanitySeverity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(SnakeCaseStrategy.class)
public class ProfanityWordRequest {

    @Size(max = 5, message = "언어 코드는 5자 이하여야 합니다.")
    private String locale;

    @NotBlank(message = "금칙어는 필수입니다.")
    @Size(max = 100, message = "금칙어는 100자 이하여야 합니다.")
    private String word;

    @NotNull(message = "카테고리는 필수입니다.")
    private ProfanityCategory category;

    @NotNull(message = "심각도는 필수입니다.")
    private ProfanitySeverity severity;

    private Boolean isActive;

    @Size(max = 500, message = "설명은 500자 이하여야 합니다.")
    private String description;
}
