package io.pinkspider.leveluptogethermvp.userservice.feed.api.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.feedservice.domain.enums.ActivityType;
import io.pinkspider.leveluptogethermvp.feedservice.domain.enums.FeedVisibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class CreateFeedRequest {

    @NotNull(message = "활동 유형을 선택해주세요")
    private ActivityType activityType;

    @NotBlank(message = "제목을 입력해주세요")
    @Size(max = 100, message = "제목은 100자 이내로 작성해주세요")
    private String title;

    @Size(max = 500, message = "설명은 500자 이내로 작성해주세요")
    private String description;

    private String referenceType;
    private Long referenceId;
    private String referenceName;

    private String imageUrl;
    private String iconUrl;

    private FeedVisibility visibility = FeedVisibility.PUBLIC;
    private Long guildId;
}
