package io.pinkspider.leveluptogethermvp.towerservice.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;
import org.springframework.data.domain.Page;

/** LUT-705: 어드민 내부 목록 한 페이지(0부터) */
@JsonNaming(SnakeCaseStrategy.class)
public record TowerAdminPage<T>(
        List<T> content, long totalElements, int totalPages, int page, int size) {

    public static <T> TowerAdminPage<T> of(Page<T> page) {
        return new TowerAdminPage<>(
                page.getContent(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.getNumber(),
                page.getSize());
    }
}
