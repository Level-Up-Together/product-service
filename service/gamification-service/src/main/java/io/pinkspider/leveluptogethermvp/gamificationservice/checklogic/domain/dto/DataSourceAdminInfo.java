package io.pinkspider.leveluptogethermvp.gamificationservice.checklogic.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.enums.CheckLogicDataSource;
import java.util.List;

@JsonNaming(SnakeCaseStrategy.class)
public record DataSourceAdminInfo(
    String code,
    String displayName,
    List<DataFieldInfo> availableFields
) {
    @JsonNaming(SnakeCaseStrategy.class)
    public record DataFieldInfo(
        String fieldName,
        String displayName
    ) {}

    public static DataSourceAdminInfo from(CheckLogicDataSource ds) {
        List<DataFieldInfo> fields = getFieldsForDataSource(ds);
        return new DataSourceAdminInfo(ds.getCode(), ds.getDisplayName(), fields);
    }

    private static List<DataFieldInfo> getFieldsForDataSource(CheckLogicDataSource ds) {
        return switch (ds) {
            case USER_STATS -> List.of(
                new DataFieldInfo("currentStreak", "현재 연속 일수"),
                new DataFieldInfo("maxStreak", "최대 연속 일수"),
                new DataFieldInfo("totalAchievementsCompleted", "완료한 업적 수"),
                new DataFieldInfo("totalTitlesAcquired", "획득한 칭호 수")
            );
            case USER_EXPERIENCE -> List.of(
                new DataFieldInfo("currentLevel", "현재 레벨"),
                new DataFieldInfo("totalExp", "총 경험치")
            );
            case USER_CATEGORY_EXPERIENCE -> List.of(
                new DataFieldInfo("category_1", "운동"),
                new DataFieldInfo("category_2", "공부"),
                new DataFieldInfo("category_3", "자기개발"),
                new DataFieldInfo("category_4", "생활습관"),
                new DataFieldInfo("category_5", "취미"),
                new DataFieldInfo("category_6", "사회활동"),
                new DataFieldInfo("category_7", "환경"),
                new DataFieldInfo("category_8", "마음챙김"),
                new DataFieldInfo("category_9", "재테크"),
                new DataFieldInfo("category_10", "커리어"),
                new DataFieldInfo("category_11", "기타")
            );
            case FRIEND_SERVICE -> List.of(
                new DataFieldInfo("friendCount", "친구 수")
            );
            case GUILD_SERVICE -> List.of(
                new DataFieldInfo("isGuildMember", "길드 가입 여부"),
                new DataFieldInfo("isGuildMaster", "길드장 여부")
            );
            case FEED_SERVICE -> List.of(
                new DataFieldInfo("totalLikesReceived", "받은 좋아요 수")
            );
        };
    }
}
