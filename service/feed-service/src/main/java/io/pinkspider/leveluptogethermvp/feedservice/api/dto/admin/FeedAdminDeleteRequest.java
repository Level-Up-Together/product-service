package io.pinkspider.leveluptogethermvp.feedservice.api.dto.admin;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.NotBlank;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record FeedAdminDeleteRequest(
        @NotBlank(message = "삭제 사유는 필수입니다") String reason, String adminInfo) {}
