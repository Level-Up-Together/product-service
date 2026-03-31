package io.pinkspider.leveluptogethermvp.gamificationservice.achievement.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.enums.TitleAcquisitionType;
import io.pinkspider.global.enums.TitlePosition;
import io.pinkspider.global.enums.TitleRarity;
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
public class TitleAdminRequest {

    @NotBlank(message = "칭호 이름은 필수입니다.")
    @Size(max = 50, message = "칭호 이름은 50자 이하이어야 합니다.")
    private String name;

    @Size(max = 50, message = "칭호 이름(영어)은 50자 이하이어야 합니다.")
    private String nameEn;

    @Size(max = 50, message = "칭호 이름(아랍어)은 50자 이하이어야 합니다.")
    private String nameAr;

    @Size(max = 50, message = "칭호 이름(일본어)은 50자 이하이어야 합니다.")
    private String nameJa;

    @Size(max = 200, message = "칭호 설명은 200자 이하이어야 합니다.")
    private String description;

    @NotNull(message = "희귀도는 필수입니다.")
    private TitleRarity rarity;

    @NotNull(message = "위치 타입은 필수입니다.")
    private TitlePosition positionType;

    @Size(max = 10, message = "색상 코드는 10자 이하이어야 합니다.")
    private String colorCode;

    private String iconUrl;

    @NotNull(message = "획득 방법은 필수입니다.")
    @Builder.Default
    private TitleAcquisitionType acquisitionType = TitleAcquisitionType.ACHIEVEMENT;

    @Size(max = 200, message = "획득 조건은 200자 이하이어야 합니다.")
    private String acquisitionCondition;

    private Boolean isActive;
}
