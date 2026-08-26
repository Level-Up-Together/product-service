package io.pinkspider.leveluptogethermvp.guildservice.domain.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.GuildMember;
import io.pinkspider.leveluptogethermvp.guildservice.domain.enums.GuildMemberRole;
import io.pinkspider.leveluptogethermvp.guildservice.domain.enums.GuildMemberStatus;
import io.pinkspider.global.enums.TitleRarity;
import io.pinkspider.global.facade.dto.EquippedItemRarityDto;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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

    // User info fields
    @Setter
    private String nickname;
    @Setter
    private String profileImageUrl;
    @Setter
    private Integer userLevel;
    @Setter
    private String equippedTitleName;
    @Setter
    private TitleRarity equippedTitleRarity;
    @Setter
    private String leftTitleName;
    @Setter
    private TitleRarity leftTitleRarity;
    @Setter
    private String rightTitleName;
    @Setter
    private TitleRarity rightTitleRarity;

    // LUT-424: 장착 아이템 타입·희귀도 (썸네일 등급 표식용). 미장착이면 빈 배열.
    @Setter
    @Builder.Default
    private List<EquippedItemRarityDto> equippedItemRarities = List.of();

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
