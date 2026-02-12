package io.pinkspider.leveluptogethermvp.noticeservice.core.feignclient;

import io.pinkspider.leveluptogethermvp.noticeservice.core.config.AdminFeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(
    name = "admin-notice-client",
    url = "${app.admin.api-url}",
    configuration = AdminFeignConfig.class
)
public interface AdminNoticeFeignClient {

    /**
     * 현재 활성화된 공지사항 목록 조회
     */
    @GetMapping("/api/admin/notices/active")
    AdminNoticeApiResponse getActiveNotices();

    /**
     * 공지사항 상세 조회 (public API로 변경 필요)
     */
    @GetMapping("/api/admin/notices/{id}/public")
    AdminNoticeSingleApiResponse getNoticeById(@PathVariable("id") Long id);
}
