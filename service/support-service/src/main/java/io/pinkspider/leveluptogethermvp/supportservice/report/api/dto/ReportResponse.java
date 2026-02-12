package io.pinkspider.leveluptogethermvp.supportservice.report.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.pinkspider.global.enums.ReportTargetType;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ReportResponse {

    private Long id;

    @JsonProperty("target_type")
    private ReportTargetType targetType;

    @JsonProperty("target_id")
    private String targetId;

    @JsonProperty("report_type")
    private ReportType reportType;

    private String reason;

    private String status;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;
}
