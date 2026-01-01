package io.pinkspider.leveluptogethermvp.userservice.feed.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ActivityType {
    // 미션 관련
    MISSION_JOINED("미션 참여", "MISSION"),
    MISSION_COMPLETED("미션 완료", "MISSION"),
    MISSION_FULL_COMPLETED("미션 전체 완료", "MISSION"),
    MISSION_SHARED("미션 공유", "MISSION"),  // 사용자가 피드에 직접 공유한 미션

    // 업적 관련
    ACHIEVEMENT_UNLOCKED("업적 달성", "ACHIEVEMENT"),
    TITLE_ACQUIRED("칭호 획득", "ACHIEVEMENT"),

    // 레벨 관련
    LEVEL_UP("레벨업", "LEVEL"),

    // 길드 관련
    GUILD_CREATED("길드 창설", "GUILD"),
    GUILD_JOINED("길드 가입", "GUILD"),
    GUILD_LEVEL_UP("길드 레벨업", "GUILD"),

    // 소셜 관련
    FRIEND_ADDED("친구 추가", "SOCIAL"),

    // 출석 관련
    ATTENDANCE_STREAK("연속 출석", "ATTENDANCE");

    private final String displayName;
    private final String category;

    public static java.util.List<ActivityType> getByCategory(String category) {
        return java.util.Arrays.stream(values())
            .filter(type -> type.getCategory().equals(category))
            .collect(java.util.stream.Collectors.toList());
    }
}
