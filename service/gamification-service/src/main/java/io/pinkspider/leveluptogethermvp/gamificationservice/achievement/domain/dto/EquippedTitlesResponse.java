package io.pinkspider.leveluptogethermvp.gamificationservice.achievement.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 장착된 LEFT/RIGHT 칭호 조합 응답
 * 예: "용감한 전사", "전설적인 챔피언"
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class EquippedTitlesResponse {

    /**
     * LEFT 칭호 (형용사/부사형)
     */
    private UserTitleResponse leftTitle;

    /**
     * RIGHT 칭호 (명사형)
     */
    private UserTitleResponse rightTitle;

    /**
     * 조합된 표시명
     * 예: "용감한 전사"
     */
    private String combinedDisplayName;
}
