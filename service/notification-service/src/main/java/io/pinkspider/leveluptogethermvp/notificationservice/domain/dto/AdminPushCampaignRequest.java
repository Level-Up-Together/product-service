package io.pinkspider.leveluptogethermvp.notificationservice.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.notificationservice.domain.enums.AdminPushTargetType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** LUT-508: 관리자 푸시 발송 요청 (admin-service 패스스루) */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(SnakeCaseStrategy.class)
public class AdminPushCampaignRequest {

    @NotBlank(message = "제목은 필수입니다.")
    @Size(max = 100, message = "제목은 100자 이하이어야 합니다.")
    private String title;

    @NotBlank(message = "내용은 필수입니다.")
    @Size(max = 500, message = "내용은 500자 이하이어야 합니다.")
    private String body;

    /** 탭 시 이동할 앱 내 경로 (예: /shop). 없으면 null */
    @Size(max = 500, message = "이동 링크는 500자 이하이어야 합니다.")
    private String actionUrl;

    @NotNull(message = "대상 유형은 필수입니다.")
    private AdminPushTargetType targetType;

    /** targetType=USERS 일 때 대상 유저 ID 목록 */
    private List<String> userIds;
}
