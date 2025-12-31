package io.pinkspider.leveluptogethermvp.supportservice.report.core.feignclient;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AdminReportCreateRequest {

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

    @JsonProperty("target_user_nickname")
    private String targetUserNickname;

    @JsonProperty("report_type")
    private String reportType;

    private String reason;
}
