package io.pinkspider.leveluptogethermvp.supportservice.report.core.feignclient;

import lombok.Data;

@Data
public class AdminReportCheckResponse {

    private String code;
    private String message;
    private Boolean value;
}
