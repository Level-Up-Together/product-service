package io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.dto.admin;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.entity.Users;
import lombok.Builder;

@Builder
@JsonNaming(SnakeCaseStrategy.class)
public record UserBriefAdminResponse(
    String id,
    String nickname,
    String picture
) {
    public static UserBriefAdminResponse from(Users user) {
        return UserBriefAdminResponse.builder()
            .id(user.getId())
            .nickname(user.getNickname())
            .picture(user.getPicture())
            .build();
    }
}
