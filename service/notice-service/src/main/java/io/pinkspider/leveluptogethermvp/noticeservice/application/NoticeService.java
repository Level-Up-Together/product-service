package io.pinkspider.leveluptogethermvp.noticeservice.application;

import io.pinkspider.leveluptogethermvp.noticeservice.api.dto.NoticeResponse;
import io.pinkspider.leveluptogethermvp.noticeservice.core.feignclient.AdminNoticeApiResponse;
import io.pinkspider.leveluptogethermvp.noticeservice.core.feignclient.AdminNoticeFeignClient;
import io.pinkspider.leveluptogethermvp.noticeservice.core.feignclient.AdminNoticeSingleApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class NoticeService {

    private final AdminNoticeFeignClient adminNoticeFeignClient;

    /**
     * 현재 활성화된 공지사항 목록 조회
     */
    public List<NoticeResponse> getActiveNotices() {
        try {
            AdminNoticeApiResponse response = adminNoticeFeignClient.getActiveNotices();

            if (response != null && response.getValue() != null) {
                log.debug("Retrieved {} active notices", response.getValue().size());
                return response.getValue();
            }

            return Collections.emptyList();
        } catch (Exception e) {
            log.error("Failed to fetch active notices from admin API", e);
            return Collections.emptyList();
        }
    }

    /**
     * 공지사항 상세 조회
     */
    public NoticeResponse getNoticeById(Long id) {
        try {
            AdminNoticeSingleApiResponse response = adminNoticeFeignClient.getNoticeById(id);

            if (response != null && response.getValue() != null) {
                log.debug("Retrieved notice: id={}", id);
                return response.getValue();
            }

            return null;
        } catch (Exception e) {
            log.error("Failed to fetch notice detail from admin API: id={}", id, e);
            return null;
        }
    }
}