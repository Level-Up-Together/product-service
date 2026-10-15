package io.pinkspider.leveluptogethermvp.userservice.preference.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.userservice.preference.domain.entity.UserUiPreference;
import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record UserUiPreferenceResponse(
        Boolean missionCompletedSectionCollapsed, List<Long> homeFeedCategoryIds) {

    public UserUiPreferenceResponse {
        if (missionCompletedSectionCollapsed == null) {
            missionCompletedSectionCollapsed = false;
        }
        homeFeedCategoryIds =
                homeFeedCategoryIds == null ? List.of() : List.copyOf(homeFeedCategoryIds);
    }

    /** LUT-437 호환 생성자 — 홈 피드 카테고리 필터 없음(전체) */
    public UserUiPreferenceResponse(Boolean missionCompletedSectionCollapsed) {
        this(missionCompletedSectionCollapsed, List.of());
    }

    /** 저장된 설정이 없는 유저 기본값 (프론트의 '미존재 = 펼침 / 전체 카테고리' 처리와 동일 결과) */
    public static UserUiPreferenceResponse defaults() {
        return new UserUiPreferenceResponse(false, List.of());
    }

    public static UserUiPreferenceResponse from(UserUiPreference preference) {
        return new UserUiPreferenceResponse(
                preference.getMissionCompletedSectionCollapsed(),
                preference.getHomeFeedCategoryIds());
    }
}
