package io.pinkspider.leveluptogethermvp.metaservice.userlevelconfig.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.metaservice.userlevelconfig.domain.entity.UserLevelConfig;
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
public class UserLevelConfigResponse {

    private Long id;
    private Integer level;
    private Integer requiredExp;
    private Integer cumulativeExp;
    private LocalDateTime createdAt;
    private LocalDateTime modifiedAt;

    public static UserLevelConfigResponse from(UserLevelConfig entity) {
        return UserLevelConfigResponse.builder()
                .id(entity.getId())
                .level(entity.getLevel())
                .requiredExp(entity.getRequiredExp())
                .cumulativeExp(entity.getCumulativeExp())
                .createdAt(entity.getCreatedAt())
                .modifiedAt(entity.getModifiedAt())
                .build();
    }
}
