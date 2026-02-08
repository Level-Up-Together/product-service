package io.pinkspider.leveluptogethermvp.userservice.feed.application;

import io.pinkspider.leveluptogethermvp.feedservice.domain.enums.ActivityType;
import io.pinkspider.leveluptogethermvp.feedservice.domain.enums.FeedVisibility;
import io.pinkspider.leveluptogethermvp.userservice.feed.domain.dto.UserTitleInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 시스템 활동 피드 생성 서비스
 * 미션 참여, 레벨업, 길드 가입 등의 이벤트에 대한 활동 피드를 생성합니다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ActivityFeedNotificationService {

    private final ActivityFeedService activityFeedService;
    private final UserTitleInfoHelper userTitleInfoHelper;

    @Transactional(transactionManager = "feedTransactionManager")
    public void notifyMissionJoined(String userId, String userNickname, String userProfileImageUrl,
                                    Integer userLevel, Long missionId, String missionTitle) {
        UserTitleInfo titleInfo = userTitleInfoHelper.getUserEquippedTitleInfo(userId);

        String title = String.format("%s 미션에 참여했습니다!", missionTitle);
        activityFeedService.createActivityFeed(userId, userNickname, userProfileImageUrl, userLevel,
            titleInfo.titleName(), titleInfo.titleRarity(), titleInfo.colorCode(),
            ActivityType.MISSION_JOINED, title, null,
            "MISSION", missionId, missionTitle,
            FeedVisibility.PUBLIC, null, null, null);
    }

    @Transactional(transactionManager = "feedTransactionManager")
    public void notifyMissionCompleted(String userId, String userNickname, String userProfileImageUrl,
                                       Integer userLevel, Long missionId, String missionTitle, int completionRate) {
        UserTitleInfo titleInfo = userTitleInfoHelper.getUserEquippedTitleInfo(userId);

        String title = String.format("%s 미션 인터벌을 완료했습니다!", missionTitle);
        String description = String.format("달성률: %d%%", completionRate);
        activityFeedService.createActivityFeed(userId, userNickname, userProfileImageUrl, userLevel,
            titleInfo.titleName(), titleInfo.titleRarity(), titleInfo.colorCode(),
            ActivityType.MISSION_COMPLETED, title, description,
            "MISSION", missionId, missionTitle,
            FeedVisibility.PUBLIC, null, null, null);
    }

    @Transactional(transactionManager = "feedTransactionManager")
    public void notifyMissionFullCompleted(String userId, String userNickname, String userProfileImageUrl,
                                           Integer userLevel, Long missionId, String missionTitle) {
        UserTitleInfo titleInfo = userTitleInfoHelper.getUserEquippedTitleInfo(userId);

        String title = String.format("%s 미션을 100%% 완료했습니다!", missionTitle);
        activityFeedService.createActivityFeed(userId, userNickname, userProfileImageUrl, userLevel,
            titleInfo.titleName(), titleInfo.titleRarity(), titleInfo.colorCode(),
            ActivityType.MISSION_FULL_COMPLETED, title, null,
            "MISSION", missionId, missionTitle,
            FeedVisibility.PUBLIC, null, null, null);
    }

    @Transactional(transactionManager = "feedTransactionManager")
    public void notifyAchievementUnlocked(String userId, String userNickname, String userProfileImageUrl,
                                          Integer userLevel, Long achievementId, String achievementName, String achievementDescription) {
        UserTitleInfo titleInfo = userTitleInfoHelper.getUserEquippedTitleInfo(userId);

        String title = String.format("[%s] 업적을 달성했습니다!", achievementName);
        activityFeedService.createActivityFeed(userId, userNickname, userProfileImageUrl, userLevel,
            titleInfo.titleName(), titleInfo.titleRarity(), titleInfo.colorCode(),
            ActivityType.ACHIEVEMENT_UNLOCKED, title, achievementDescription,
            "ACHIEVEMENT", achievementId, achievementName,
            FeedVisibility.PUBLIC, null, null, null);
    }

    @Transactional(transactionManager = "feedTransactionManager")
    public void notifyTitleAcquired(String userId, String userNickname, String userProfileImageUrl,
                                    Integer userLevel, Long titleId, String titleName) {
        UserTitleInfo titleInfo = userTitleInfoHelper.getUserEquippedTitleInfo(userId);

        String title = String.format("[%s] 칭호를 획득했습니다!", titleName);
        activityFeedService.createActivityFeed(userId, userNickname, userProfileImageUrl, userLevel,
            titleInfo.titleName(), titleInfo.titleRarity(), titleInfo.colorCode(),
            ActivityType.TITLE_ACQUIRED, title, null,
            "TITLE", titleId, titleName,
            FeedVisibility.PUBLIC, null, null, null);
    }

    @Transactional(transactionManager = "feedTransactionManager")
    public void notifyLevelUp(String userId, String userNickname, String userProfileImageUrl,
                              int newLevel, int totalExp) {
        UserTitleInfo titleInfo = userTitleInfoHelper.getUserEquippedTitleInfo(userId);

        String title = String.format("레벨 %d 달성!", newLevel);
        String description = String.format("누적 경험치: %,d", totalExp);
        activityFeedService.createActivityFeed(userId, userNickname, userProfileImageUrl, newLevel,
            titleInfo.titleName(), titleInfo.titleRarity(), titleInfo.colorCode(),
            ActivityType.LEVEL_UP, title, description,
            "LEVEL", (long) newLevel, "레벨 " + newLevel,
            FeedVisibility.PUBLIC, null, null, null);
    }

    @Transactional(transactionManager = "feedTransactionManager")
    public void notifyGuildCreated(String userId, String userNickname, String userProfileImageUrl,
                                   Integer userLevel, Long guildId, String guildName) {
        UserTitleInfo titleInfo = userTitleInfoHelper.getUserEquippedTitleInfo(userId);

        String title = String.format("[%s] 길드를 창설했습니다!", guildName);
        activityFeedService.createActivityFeed(userId, userNickname, userProfileImageUrl, userLevel,
            titleInfo.titleName(), titleInfo.titleRarity(), titleInfo.colorCode(),
            ActivityType.GUILD_CREATED, title, null,
            "GUILD", guildId, guildName,
            FeedVisibility.PUBLIC, guildId, null, null);
    }

    @Transactional(transactionManager = "feedTransactionManager")
    public void notifyGuildJoined(String userId, String userNickname, String userProfileImageUrl,
                                  Integer userLevel, Long guildId, String guildName) {
        UserTitleInfo titleInfo = userTitleInfoHelper.getUserEquippedTitleInfo(userId);

        String title = String.format("[%s] 길드에 가입했습니다!", guildName);
        activityFeedService.createActivityFeed(userId, userNickname, userProfileImageUrl, userLevel,
            titleInfo.titleName(), titleInfo.titleRarity(), titleInfo.colorCode(),
            ActivityType.GUILD_JOINED, title, null,
            "GUILD", guildId, guildName,
            FeedVisibility.PUBLIC, guildId, null, null);
    }

    @Transactional(transactionManager = "feedTransactionManager")
    public void notifyGuildLevelUp(String userId, String userNickname, String userProfileImageUrl,
                                   Integer userLevel, Long guildId, String guildName, int newGuildLevel) {
        UserTitleInfo titleInfo = userTitleInfoHelper.getUserEquippedTitleInfo(userId);

        String title = String.format("[%s] 길드가 레벨 %d로 성장했습니다!", guildName, newGuildLevel);
        activityFeedService.createActivityFeed(userId, userNickname, userProfileImageUrl, userLevel,
            titleInfo.titleName(), titleInfo.titleRarity(), titleInfo.colorCode(),
            ActivityType.GUILD_LEVEL_UP, title, null,
            "GUILD", guildId, guildName,
            FeedVisibility.GUILD, guildId, null, null);
    }

    @Transactional(transactionManager = "feedTransactionManager")
    public void notifyFriendAdded(String userId, String userNickname, String userProfileImageUrl,
                                  Integer userLevel, String friendId, String friendNickname) {
        UserTitleInfo titleInfo = userTitleInfoHelper.getUserEquippedTitleInfo(userId);

        String title = String.format("%s님과 친구가 되었습니다!", friendNickname);
        activityFeedService.createActivityFeed(userId, userNickname, userProfileImageUrl, userLevel,
            titleInfo.titleName(), titleInfo.titleRarity(), titleInfo.colorCode(),
            ActivityType.FRIEND_ADDED, title, null,
            "USER", null, friendNickname,
            FeedVisibility.FRIENDS, null, null, null);
    }

    @Transactional(transactionManager = "feedTransactionManager")
    public void notifyAttendanceStreak(String userId, String userNickname, String userProfileImageUrl,
                                       Integer userLevel, int streakDays) {
        UserTitleInfo titleInfo = userTitleInfoHelper.getUserEquippedTitleInfo(userId);

        String title = String.format("%d일 연속 출석 달성!", streakDays);
        activityFeedService.createActivityFeed(userId, userNickname, userProfileImageUrl, userLevel,
            titleInfo.titleName(), titleInfo.titleRarity(), titleInfo.colorCode(),
            ActivityType.ATTENDANCE_STREAK, title, null,
            "ATTENDANCE", (long) streakDays, streakDays + "일 연속 출석",
            FeedVisibility.PUBLIC, null, null, null);
    }
}
