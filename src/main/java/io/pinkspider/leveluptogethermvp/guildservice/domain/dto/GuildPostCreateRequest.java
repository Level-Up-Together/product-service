package io.pinkspider.leveluptogethermvp.guildservice.domain.dto;

import io.pinkspider.leveluptogethermvp.guildservice.domain.enums.GuildPostType;
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
public class GuildPostCreateRequest {

    @NotBlank(message = "제목은 필수입니다.")
    @Size(max = 200, message = "제목은 200자 이하여야 합니다.")
    private String title;

    @NotBlank(message = "내용은 필수입니다.")
    private String content;

    @Builder.Default
    private GuildPostType postType = GuildPostType.NORMAL;

    @Builder.Default
    private Boolean isPinned = false;
}
