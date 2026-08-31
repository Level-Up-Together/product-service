package io.pinkspider.leveluptogethermvp.userservice.preference.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * LUT-437: UI 환경설정 부분 업데이트 요청 — null 필드는 변경하지 않는다 (알림 설정과 동일 방식).
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class UserUiPreferenceRequest {

    private Boolean missionCompletedSectionCollapsed;
}
