package io.pinkspider.leveluptogethermvp.chatservice.domain.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DirectMessageRequest {

    @Size(max = 1000, message = "메시지는 1000자를 초과할 수 없습니다.")
    private String content;

    @JsonProperty("image_url")
    @Size(max = 500, message = "이미지 URL은 500자를 초과할 수 없습니다.")
    private String imageUrl;

    /**
     * 본문 또는 사진 중 하나는 있어야 한다 (LUT-590). 예전엔 본문 {@code @NotBlank} 라 사진만 보낸 DM 이 400 이었다 — 구앱은 항상 본문을
     * 보내므로 완화해도 하위호환이 유지된다.
     */
    @JsonIgnore
    @AssertTrue(message = "메시지 내용은 필수입니다.")
    public boolean isContentOrImagePresent() {
        return (content != null && !content.isBlank()) || (imageUrl != null && !imageUrl.isBlank());
    }
}
