package io.pinkspider.leveluptogethermvp.userservice.terms.domain.dto.admin;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;
import org.springframework.data.domain.Page;

@JsonNaming(SnakeCaseStrategy.class)
public record UserTermAgreementAdminPageResponse(
    List<UserTermAgreementAdminResponse> content,
    int totalPages,
    long totalElements,
    int number,
    int size,
    boolean first,
    boolean last
) {

    public static UserTermAgreementAdminPageResponse from(Page<UserTermAgreementAdminResponse> page) {
        return new UserTermAgreementAdminPageResponse(
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
