package io.pinkspider.leveluptogethermvp.guildservice.domain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 길드 초대 요청 DTO
 */
public record GuildInvitationRequest(
    @NotBlank(message = "초대 대상자 ID는 필수입니다.")
    @JsonProperty("invitee_id")
    String inviteeId,

    @Size(max = 500, message = "초대 메시지는 500자를 초과할 수 없습니다.")
    @JsonProperty("message")
    String message
) {
}
