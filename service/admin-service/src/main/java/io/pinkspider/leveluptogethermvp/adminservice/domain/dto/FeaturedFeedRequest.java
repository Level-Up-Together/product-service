package io.pinkspider.leveluptogethermvp.adminservice.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.NotNull;
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
public class FeaturedFeedRequest {

    private Long categoryId;

    @NotNull(message = "피드 ID는 필수입니다")
    private Long feedId;

    private Integer displayOrder;

    private Boolean isActive;

    private LocalDateTime startAt;

    private LocalDateTime endAt;

    private String createdBy;

    private String modifiedBy;
}
