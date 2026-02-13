package io.pinkspider.leveluptogethermvp.userservice.terms.domain.dto.admin;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.entity.TermVersion;
import java.time.LocalDateTime;

@JsonNaming(SnakeCaseStrategy.class)
public record TermVersionAdminResponse(
    Long id,
    Long termsId,
    String termsCode,
    String termsTitle,
    String version,
    String content,
    String createdBy,
    LocalDateTime createdAt,
    LocalDateTime modifiedAt
) {

    public static TermVersionAdminResponse from(TermVersion entity) {
        return new TermVersionAdminResponse(
            entity.getId(),
            entity.getTerms().getId(),
            entity.getTerms().getCode(),
            entity.getTerms().getTitle(),
            entity.getVersion(),
            entity.getContent(),
            entity.getCreatedBy(),
            entity.getCreatedAt(),
            entity.getModifiedAt()
        );
    }

    public static TermVersionAdminResponse fromSimple(TermVersion entity) {
        return new TermVersionAdminResponse(
            entity.getId(),
            null,
            null,
            null,
            entity.getVersion(),
            null,
            entity.getCreatedBy(),
            entity.getCreatedAt(),
            null
        );
    }
}
