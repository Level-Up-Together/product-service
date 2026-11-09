package io.pinkspider.leveluptogethermvp.userservice.preference.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.userservice.preference.domain.entity.UserUiPreference;
import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record UserUiPreferenceResponse(
        Boolean missionCompletedSectionCollapsed,
        List<Long> homeFeedCategoryIds,
        Boolean towerAutoSeat,
        Boolean towerAutoSeatNoticeSeen) {
    public UserUiPreferenceResponse {
        if (missionCompletedSectionCollapsed == null) {
            missionCompletedSectionCollapsed = false;
        }
        homeFeedCategoryIds =
                homeFeedCategoryIds == null ? List.of() : List.copyOf(homeFeedCategoryIds);
        // LUT-632: 탑 자동 착석은 기본 켬, 첫 안내는 기본 미확인
        if (towerAutoSeat == null) {
            towerAutoSeat = true;
        }
        if (towerAutoSeatNoticeSeen == null) {
            towerAutoSeatNoticeSeen = false;
        }
    }

    /** LUT-548 호환 생성자 — 탑 자동 착석 기본값 */
    public UserUiPreferenceResponse(
            Boolean missionCompletedSectionCollapsed, List<Long> homeFeedCategoryIds) {
        this(missionCompletedSectionCollapsed, homeFeedCategoryIds, null, null);
    }

    /** LUT-437 호환 생성자 — 홈 피드 카테고리 필터 없음(전체) */
    public UserUiPreferenceResponse(Boolean missionCompletedSectionCollapsed) {
        this(missionCompletedSectionCollapsed, List.of());
    }

    /** 저장된 설정이 없는 유저 기본값 (프론트의 '미존재 = 펼침 / 전체 카테고리 / 자동 착석 켬' 처리와 동일 결과) */
    public static UserUiPreferenceResponse defaults() {
        return new UserUiPreferenceResponse(false, List.of(), true, false);
    }

    public static UserUiPreferenceResponse from(UserUiPreference preference) {
        return new UserUiPreferenceResponse(
                preference.getMissionCompletedSectionCollapsed(),
                preference.getHomeFeedCategoryIds(),
                preference.getTowerAutoSeat(),
                preference.getTowerAutoSeatNoticeSeen());
    }
}
