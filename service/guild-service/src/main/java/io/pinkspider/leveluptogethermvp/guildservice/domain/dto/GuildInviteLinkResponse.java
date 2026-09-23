package io.pinkspider.leveluptogethermvp.guildservice.domain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

/**
 * 길드 초대 링크 응답 DTO (LUT-519).
 *
 * <p>{@code code} 는 길드당 1개·불변. {@code invitePath} 는 프론트가 origin 을 붙여 완성할 상대 경로.
 */
@Builder
public record GuildInviteLinkResponse(
    @JsonProperty("code") String code, @JsonProperty("invite_path") String invitePath) {}
