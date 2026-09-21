package io.pinkspider.leveluptogethermvp.notificationservice.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;
import org.springframework.data.domain.Page;

/** LUT-508: 관리자 푸시 발송 이력 페이지 응답 */
@JsonNaming(SnakeCaseStrategy.class)
public record AdminPushCampaignPageResponse(
        List<AdminPushCampaignResponse> content,
        int totalPages,
        long totalElements,
        int number,
        int size,
        boolean first,
        boolean last) {

    public static AdminPushCampaignPageResponse from(Page<AdminPushCampaignResponse> page) {
        return new AdminPushCampaignPageResponse(
                page.getContent(),
                page.getTotalPages(),
                page.getTotalElements(),
                page.getNumber(),
                page.getSize(),
                page.isFirst(),
                page.isLast());
    }
}
