package io.pinkspider.leveluptogethermvp.guildservice.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.GuildMember;
import io.pinkspider.leveluptogethermvp.guildservice.domain.enums.GuildMemberRole;
import io.pinkspider.leveluptogethermvp.guildservice.domain.enums.GuildMemberStatus;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class GuildMemberResponse {

    private Long id;
    private Long guildId;
    private String userId;
    private GuildMemberRole role;
    private GuildMemberStatus status;
    private LocalDateTime joinedAt;

    public static GuildMemberResponse from(GuildMember member) {
        return GuildMemberResponse.builder()
            .id(member.getId())
            .guildId(member.getGuild().getId())
            .userId(member.getUserId())
            .role(member.getRole())
            .status(member.getStatus())
            .joinedAt(member.getJoinedAt())
            .build();
    }
}
