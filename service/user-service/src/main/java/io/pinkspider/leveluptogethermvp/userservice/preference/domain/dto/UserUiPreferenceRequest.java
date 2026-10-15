package io.pinkspider.leveluptogethermvp.userservice.preference.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** LUT-437: UI 환경설정 부분 업데이트 요청 — null 필드는 변경하지 않는다 (알림 설정과 동일 방식). */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class UserUiPreferenceRequest {

    private Boolean missionCompletedSectionCollapsed;

    /** LUT-548: 홈 피드 카테고리 필터. null=변경 없음, 빈 배열=전체(필터 해제). 존재하지 않는 ID 는 무시된다. */
    private List<Long> homeFeedCategoryIds;
}
