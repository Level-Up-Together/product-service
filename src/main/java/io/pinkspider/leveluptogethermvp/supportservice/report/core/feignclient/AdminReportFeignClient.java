package io.pinkspider.leveluptogethermvp.supportservice.report.core.feignclient;

import io.pinkspider.leveluptogethermvp.noticeservice.core.config.AdminFeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(
    name = "admin-report-client",
    url = "${app.admin.api-url}",
    configuration = AdminFeignConfig.class
)
public interface AdminReportFeignClient {

    @PostMapping("/api/admin/reports")
    AdminReportApiResponse createReport(@RequestBody AdminReportCreateRequest request);
}
