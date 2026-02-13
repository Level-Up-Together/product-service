package io.pinkspider.leveluptogethermvp.gamificationservice.mvphistory.domain.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.DailyMvpHistory;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(SnakeCaseStrategy.class)
public class MvpHistoryAdminResponse {

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate mvpDate;
    private Integer mvpRank;
    private String userId;
    private String nickname;
    private String picture;
    private Integer userLevel;
    private Long earnedExp;
    private String topCategoryName;
    private Long topCategoryId;
    private Long topCategoryExp;
    private String titleName;
    private String titleRarity;

    public static MvpHistoryAdminResponse from(DailyMvpHistory history) {
        return MvpHistoryAdminResponse.builder()
            .mvpDate(history.getMvpDate())
            .mvpRank(history.getMvpRank())
            .userId(history.getUserId())
            .nickname(history.getNickname())
            .picture(history.getPicture())
            .userLevel(history.getUserLevel())
            .earnedExp(history.getEarnedExp())
            .topCategoryName(history.getTopCategoryName())
            .topCategoryId(history.getTopCategoryId())
            .topCategoryExp(history.getTopCategoryExp())
            .titleName(history.getTitleName())
            .titleRarity(history.getTitleRarity() != null ? history.getTitleRarity().name() : null)
            .build();
    }
}
