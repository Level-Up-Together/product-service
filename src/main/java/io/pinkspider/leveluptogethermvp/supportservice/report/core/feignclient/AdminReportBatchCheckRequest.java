package io.pinkspider.leveluptogethermvp.supportservice.report.core.feignclient;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminReportBatchCheckRequest {

    @JsonProperty("target_type")
    private String targetType;

    @JsonProperty("target_ids")
    private List<String> targetIds;
}
