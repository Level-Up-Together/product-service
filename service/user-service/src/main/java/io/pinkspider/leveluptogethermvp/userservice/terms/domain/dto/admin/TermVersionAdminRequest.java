package io.pinkspider.leveluptogethermvp.userservice.terms.domain.dto.admin;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(SnakeCaseStrategy.class)
public record TermVersionAdminRequest(
    String version,
    String content
) {
}
