package io.pinkspider.leveluptogethermvp.gamificationservice.checklogic.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.enums.CheckLogicDataSource;
import io.pinkspider.leveluptogethermvp.metaservice.domain.dto.MissionCategoryResponse;
import java.util.Comparator;
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

    /**
     * QA-145: USER_CATEGORY_EXPERIENCE 의 availableFields 는 실시간 mission_category 에서 생성한다.
     * activeCategories 는 meta-service 가 제공하는 활성 카테고리 목록.
     */
    public static DataSourceAdminInfo from(CheckLogicDataSource ds, List<MissionCategoryResponse> activeCategories) {
        return new DataSourceAdminInfo(ds.getCode(), ds.getDisplayName(), getFieldsForDataSource(ds, activeCategories));
    }

    private static List<DataFieldInfo> getFieldsForDataSource(
        CheckLogicDataSource ds,
        List<MissionCategoryResponse> activeCategories) {
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
            case USER_CATEGORY_EXPERIENCE -> activeCategories.stream()
                .sorted(Comparator.comparing(
                    MissionCategoryResponse::getDisplayOrder,
                    Comparator.nullsLast(Comparator.naturalOrder())))
                .map(c -> new DataFieldInfo("category_" + c.getId(), c.getName()))
                .toList();
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
