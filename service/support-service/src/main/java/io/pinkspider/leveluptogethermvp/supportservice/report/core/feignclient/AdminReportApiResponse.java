package io.pinkspider.leveluptogethermvp.supportservice.report.core.feignclient;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class AdminReportApiResponse {

    private String code;
    private String message;
    private ReportValue value;

    @Data
    public static class ReportValue {
        private Long id;

        @JsonProperty("reporter_id")
        private String reporterId;

        @JsonProperty("reporter_nickname")
        private String reporterNickname;

        @JsonProperty("target_type")
        private String targetType;

        @JsonProperty("target_id")
        private String targetId;

        @JsonProperty("target_user_id")
        private String targetUserId;

        @JsonProperty("report_type")
        private String reportType;

        private String reason;
        private String status;

        @JsonProperty("created_at")
        private LocalDateTime createdAt;
    }
}
