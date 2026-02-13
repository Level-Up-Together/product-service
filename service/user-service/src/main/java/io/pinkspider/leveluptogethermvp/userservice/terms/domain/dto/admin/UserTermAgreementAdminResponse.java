package io.pinkspider.leveluptogethermvp.userservice.terms.domain.dto.admin;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.entity.UserTermAgreement;
import java.time.LocalDateTime;

@JsonNaming(SnakeCaseStrategy.class)
public record UserTermAgreementAdminResponse(
    Long id,
    String userId,
    Long termVersionId,
    String termVersion,
    Long termsId,
    String termsCode,
    String termsTitle,
    String termsType,
    Boolean isRequired,
    Boolean isAgreed,
    LocalDateTime agreedAt
) {

    public static UserTermAgreementAdminResponse from(UserTermAgreement entity) {
        return new UserTermAgreementAdminResponse(
            entity.getId(),
            entity.getUsers().getId(),
            entity.getTermVersion().getId(),
            entity.getTermVersion().getVersion(),
            entity.getTermVersion().getTerms().getId(),
            entity.getTermVersion().getTerms().getCode(),
            entity.getTermVersion().getTerms().getTitle(),
            entity.getTermVersion().getTerms().getType(),
            entity.getTermVersion().getTerms().getIsRequired(),
            entity.getIsAgreed(),
            entity.getAgreedAt()
        );
    }
}
