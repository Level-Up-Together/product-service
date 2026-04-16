package io.pinkspider.leveluptogethermvp.gamificationservice.achievement.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity.UserTitle;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(SnakeCaseStrategy.class)
public class TitleGrantAdminResponse {

    private Long id;
    private String userId;
    private String userNickname;
    private Long titleId;
    private String titleName;
    private String titleRarity;
    private String titlePositionType;
    private String reason;
    private Long grantedBy;
    private LocalDateTime grantedAt;

    public static TitleGrantAdminResponse from(UserTitle userTitle, String userNickname) {
        return TitleGrantAdminResponse.builder()
            .id(userTitle.getId())
            .userId(userTitle.getUserId())
            .userNickname(userNickname)
            .titleId(userTitle.getTitle().getId())
            .titleName(userTitle.getTitle().getName())
            .titleRarity(userTitle.getTitle().getRarity().name())
            .titlePositionType(userTitle.getTitle().getPositionType().name())
            .reason(userTitle.getGrantReason())
            .grantedBy(userTitle.getGrantedBy())
            .grantedAt(userTitle.getAcquiredAt())
            .build();
    }
}
