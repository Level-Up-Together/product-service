package io.pinkspider.leveluptogethermvp.userservice.terms.domain.dto.admin;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.entity.Term;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.entity.TermVersion;
import java.time.LocalDateTime;
import java.util.Comparator;

@JsonNaming(SnakeCaseStrategy.class)
public record TermsAdminResponse(
    Long id,
    String code,
    String title,
    String description,
    String type,
    Boolean isRequired,
    String createdBy,
    String modifiedBy,
    LocalDateTime createdAt,
    LocalDateTime modifiedAt,
    TermVersionAdminResponse latestVersion,
    Integer versionCount
) {

    public static TermsAdminResponse from(Term entity) {
        TermVersion latest = entity.getTermVersions() != null && !entity.getTermVersions().isEmpty()
            ? entity.getTermVersions().stream()
                .max(Comparator.comparing(TermVersion::getId))
                .orElse(null)
            : null;
        return new TermsAdminResponse(
            entity.getId(),
            entity.getCode(),
            entity.getTitle(),
            entity.getDescription(),
            entity.getType(),
            entity.getIsRequired(),
            entity.getCreatedBy(),
            entity.getModifiedBy(),
            entity.getCreatedAt(),
            entity.getModifiedAt(),
            latest != null ? TermVersionAdminResponse.fromSimple(latest) : null,
            entity.getTermVersions() != null ? entity.getTermVersions().size() : 0
        );
    }

    public static TermsAdminResponse fromSimple(Term entity) {
        return new TermsAdminResponse(
            entity.getId(),
            entity.getCode(),
            entity.getTitle(),
            entity.getDescription(),
            entity.getType(),
            entity.getIsRequired(),
            entity.getCreatedBy(),
            entity.getModifiedBy(),
            entity.getCreatedAt(),
            entity.getModifiedAt(),
            null,
            null
        );
    }
}
