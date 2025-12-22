package io.pinkspider.leveluptogethermvp.userservice.mypage.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 칭호 변경 응답 DTO
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class TitleChangeResponse {

    private String message;
    private MyPageResponse.EquippedTitleInfo leftTitle;
    private MyPageResponse.EquippedTitleInfo rightTitle;
}
