package io.pinkspider.leveluptogethermvp.userservice.preference.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.userservice.preference.domain.entity.UserUiPreference;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record UserUiPreferenceResponse(
    Boolean missionCompletedSectionCollapsed
) {

    /** 저장된 설정이 없는 유저 기본값 (프론트의 '미존재 = 펼침' 처리와 동일 결과) */
    public static UserUiPreferenceResponse defaults() {
        return new UserUiPreferenceResponse(false);
    }

    public static UserUiPreferenceResponse from(UserUiPreference preference) {
        return new UserUiPreferenceResponse(
            preference.getMissionCompletedSectionCollapsed() != null
                ? preference.getMissionCompletedSectionCollapsed()
                : false);
    }
}
