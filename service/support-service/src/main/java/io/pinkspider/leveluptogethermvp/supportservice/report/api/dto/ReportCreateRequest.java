package io.pinkspider.leveluptogethermvp.supportservice.report.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.pinkspider.global.enums.ReportTargetType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ReportCreateRequest(
    @NotNull
    @JsonProperty("target_type")
    ReportTargetType targetType,

    @NotBlank
    @JsonProperty("target_id")
    String targetId,

    @JsonProperty("target_user_id")
    String targetUserId,

    @NotNull
    @JsonProperty("report_type")
    ReportType reportType,

    String reason
) {
}
