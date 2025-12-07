package io.pinkspider.leveluptogethermvp.userservice.achievement.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AchievementCategory {
    MISSION("미션", "미션 관련 업적"),
    GUILD("길드", "길드 관련 업적"),
    SOCIAL("소셜", "소셜 활동 관련 업적"),
    LEVEL("레벨", "레벨 관련 업적"),
    STREAK("연속", "연속 활동 관련 업적"),
    SPECIAL("특별", "특별 업적");

    private final String name;
    private final String description;
}
