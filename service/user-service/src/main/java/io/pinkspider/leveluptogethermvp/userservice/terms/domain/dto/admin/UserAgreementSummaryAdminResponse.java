package io.pinkspider.leveluptogethermvp.userservice.terms.domain.dto.admin;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.LocalDateTime;

@JsonNaming(SnakeCaseStrategy.class)
public record UserAgreementSummaryAdminResponse(
    String userId,
    Long totalTermsCount,
    Long agreedCount,
    Long requiredTermsCount,
    Long requiredAgreedCount,
    LocalDateTime lastAgreedAt
) {
}
