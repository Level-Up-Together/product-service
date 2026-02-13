package io.pinkspider.leveluptogethermvp.userservice.terms.domain.dto.admin;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;
import org.springframework.data.domain.Page;

@JsonNaming(SnakeCaseStrategy.class)
public record UserAgreementSummaryAdminPageResponse(
    List<UserAgreementSummaryAdminResponse> content,
    int totalPages,
    long totalElements,
    int number,
    int size,
    boolean first,
    boolean last
) {

    public static UserAgreementSummaryAdminPageResponse from(Page<UserAgreementSummaryAdminResponse> page) {
        return new UserAgreementSummaryAdminPageResponse(
            page.getContent(),
            page.getTotalPages(),
            page.getTotalElements(),
            page.getNumber(),
            page.getSize(),
            page.isFirst(),
            page.isLast()
        );
    }
}
