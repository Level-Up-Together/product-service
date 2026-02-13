package io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.dto.admin;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;
import lombok.Builder;
import org.springframework.data.domain.Page;

@Builder
@JsonNaming(SnakeCaseStrategy.class)
public record UserAdminPageResponse(
    List<UserAdminResponse> content,
    int page,
    int size,
    long totalElements,
    int totalPages,
    boolean first,
    boolean last
) {
    public static UserAdminPageResponse from(Page<UserAdminResponse> p) {
        return UserAdminPageResponse.builder()
            .content(p.getContent())
            .page(p.getNumber())
            .size(p.getSize())
            .totalElements(p.getTotalElements())
            .totalPages(p.getTotalPages())
            .first(p.isFirst())
            .last(p.isLast())
            .build();
    }
}
