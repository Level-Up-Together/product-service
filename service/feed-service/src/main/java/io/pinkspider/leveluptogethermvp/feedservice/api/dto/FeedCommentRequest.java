package io.pinkspider.leveluptogethermvp.feedservice.api.dto;

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

    /** 대댓글 작성 시 부모 댓글 ID. null이면 최상위 댓글. 부모가 이미 대댓글(parent_id가 채워진 행)이면 1-depth 제한으로 거부된다. */
    private Long parentId;
}
