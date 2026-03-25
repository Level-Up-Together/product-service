package io.pinkspider.leveluptogethermvp.profanity.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.profanity.domain.entity.ProfanityWord;
import io.pinkspider.leveluptogethermvp.profanity.domain.enums.ProfanityCategory;
import io.pinkspider.leveluptogethermvp.profanity.domain.enums.ProfanitySeverity;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(SnakeCaseStrategy.class)
public class ProfanityWordResponse {

    private Long id;
    private String locale;
    private String word;
    private ProfanityCategory category;
    private String categoryName;
    private ProfanitySeverity severity;
    private String severityName;
    private Boolean isActive;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime modifiedAt;

    public static ProfanityWordResponse from(ProfanityWord entity) {
        return ProfanityWordResponse.builder()
            .id(entity.getId())
            .locale(entity.getLocale())
            .word(entity.getWord())
            .category(entity.getCategory())
            .categoryName(entity.getCategory().getName())
            .severity(entity.getSeverity())
            .severityName(entity.getSeverity().getName())
            .isActive(entity.getIsActive())
            .description(entity.getDescription())
            .createdAt(entity.getCreatedAt())
            .modifiedAt(entity.getModifiedAt())
            .build();
    }
}
