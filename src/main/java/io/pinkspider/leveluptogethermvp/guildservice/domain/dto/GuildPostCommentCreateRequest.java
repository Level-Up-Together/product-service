package io.pinkspider.leveluptogethermvp.guildservice.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GuildPostCommentCreateRequest {

    @NotBlank(message = "댓글 내용은 필수입니다.")
    @Size(max = 1000, message = "댓글은 1000자 이하여야 합니다.")
    private String content;

    private Long parentId;
}
