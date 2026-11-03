package io.pinkspider.leveluptogethermvp.guildservice.domain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

/**
 * 길드 초대 링크 합류 응답 DTO (LUT-519).
 *
 * <p>합류(또는 이미 멤버)한 길드의 ID — 프론트가 길드 상세로 이동할 때 사용.
 */
@Builder
public record GuildInviteJoinResponse(@JsonProperty("guild_id") Long guildId) {}
