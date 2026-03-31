package io.pinkspider.leveluptogethermvp.gamificationservice.achievement.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(SnakeCaseStrategy.class)
public class AchievementAdminRequest {

    @NotBlank(message = "업적 이름은 필수입니다.")
    @Size(max = 100, message = "업적 이름은 100자 이하이어야 합니다.")
    private String name;

    @Size(max = 100, message = "업적 이름(영어)은 100자 이하이어야 합니다.")
    private String nameEn;

    @Size(max = 100, message = "업적 이름(아랍어)은 100자 이하이어야 합니다.")
    private String nameAr;

    @Size(max = 100, message = "업적 이름(일본어)은 100자 이하이어야 합니다.")
    private String nameJa;

    @Size(max = 500, message = "업적 설명은 500자 이하이어야 합니다.")
    private String description;

    @Size(max = 500, message = "업적 설명(영어)은 500자 이하이어야 합니다.")
    private String descriptionEn;

    @Size(max = 500, message = "업적 설명(아랍어)은 500자 이하이어야 합니다.")
    private String descriptionAr;

    @Size(max = 500, message = "업적 설명(일본어)은 500자 이하이어야 합니다.")
    private String descriptionJa;

    @NotNull(message = "업적 카테고리 ID는 필수입니다.")
    private Long categoryId;

    private Long missionCategoryId;

    @Size(max = 50, message = "미션 카테고리명은 50자 이하이어야 합니다.")
    private String missionCategoryName;

    @NotNull(message = "체크 로직 유형 ID는 필수입니다.")
    private Long checkLogicTypeId;

    private String iconUrl;

    @NotNull(message = "달성 필요 횟수는 필수입니다.")
    @Min(value = 1, message = "달성 필요 횟수는 1 이상이어야 합니다.")
    private Integer requiredCount;

    @Min(value = 0, message = "보상 경험치는 0 이상이어야 합니다.")
    private Integer rewardExp;

    private Long rewardTitleId;

    private Boolean isHidden;

    private Boolean isActive;

    private Long eventId;
}
