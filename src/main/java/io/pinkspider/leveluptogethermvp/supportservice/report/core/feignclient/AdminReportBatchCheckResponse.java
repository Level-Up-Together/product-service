package io.pinkspider.leveluptogethermvp.supportservice.report.core.feignclient;

import java.util.Map;
import lombok.Data;

@Data
public class AdminReportBatchCheckResponse {

    private String code;
    private String message;
    private Map<String, Boolean> value;
}
