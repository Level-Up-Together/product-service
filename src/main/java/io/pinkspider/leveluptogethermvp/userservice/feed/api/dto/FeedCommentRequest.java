package io.pinkspider.leveluptogethermvp.userservice.feed.api.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class FeedCommentRequest {

    @NotBlank(message = "댓글 내용을 입력해주세요")
    @Size(max = 500, message = "댓글은 500자 이내로 작성해주세요")
    private String content;
}
