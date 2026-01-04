package io.pinkspider.leveluptogethermvp.userservice.achievement.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AchievementType {
    // 미션 관련
    FIRST_MISSION_COMPLETE("첫 미션 완료", AchievementCategory.MISSION, 1),
    MISSION_COMPLETE_10("미션 10회 완료", AchievementCategory.MISSION, 10),
    MISSION_COMPLETE_50("미션 50회 완료", AchievementCategory.MISSION, 50),
    MISSION_COMPLETE_100("미션 100회 완료", AchievementCategory.MISSION, 100),
    MISSION_COMPLETE_500("미션 500회 완료", AchievementCategory.MISSION, 500),
    MISSION_FULL_COMPLETE_1("첫 미션 전체 완료", AchievementCategory.MISSION, 1),
    MISSION_FULL_COMPLETE_10("미션 전체 완료 10회", AchievementCategory.MISSION, 10),

    // 길드 관련
    FIRST_GUILD_JOIN("첫 길드 가입", AchievementCategory.GUILD, 1),
    GUILD_MASTER("길드 마스터", AchievementCategory.GUILD, 1),
    GUILD_MISSION_COMPLETE_10("길드 미션 10회 완료", AchievementCategory.GUILD, 10),
    GUILD_MISSION_COMPLETE_50("길드 미션 50회 완료", AchievementCategory.GUILD, 50),
    GUILD_MISSION_COMPLETE_100("길드 미션 100회 완료", AchievementCategory.GUILD, 100),

    // 레벨 관련
    REACH_LEVEL_5("레벨 5 달성", AchievementCategory.LEVEL, 5),
    REACH_LEVEL_10("레벨 10 달성", AchievementCategory.LEVEL, 10),
    REACH_LEVEL_20("레벨 20 달성", AchievementCategory.LEVEL, 20),
    REACH_LEVEL_50("레벨 50 달성", AchievementCategory.LEVEL, 50),
    REACH_LEVEL_100("레벨 100 달성", AchievementCategory.LEVEL, 100),

    // 연속 활동 관련
    STREAK_3_DAYS("3일 연속 미션 수행", AchievementCategory.STREAK, 3),
    STREAK_7_DAYS("7일 연속 미션 수행", AchievementCategory.STREAK, 7),
    STREAK_14_DAYS("14일 연속 미션 수행", AchievementCategory.STREAK, 14),
    STREAK_30_DAYS("30일 연속 미션 수행", AchievementCategory.STREAK, 30),
    STREAK_100_DAYS("100일 연속 미션 수행", AchievementCategory.STREAK, 100),

    // 소셜 관련
    FIRST_FRIEND("첫 친구", AchievementCategory.SOCIAL, 1),
    FRIENDS_10("친구 10명", AchievementCategory.SOCIAL, 10),
    FRIENDS_50("친구 50명", AchievementCategory.SOCIAL, 50),
    FIRST_LIKE("첫 좋아요", AchievementCategory.SOCIAL, 1),
    LIKES_100("좋아요 100개", AchievementCategory.SOCIAL, 100),

    // 특별 업적
    EARLY_ADOPTER("얼리 어답터", AchievementCategory.SPECIAL, 1),
    PERFECTIONIST("완벽주의자", AchievementCategory.SPECIAL, 1);

    private final String displayName;
    private final AchievementCategory category;
    private final int requiredCount;
}
