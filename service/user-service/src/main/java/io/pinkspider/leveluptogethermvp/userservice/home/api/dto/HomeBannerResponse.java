package io.pinkspider.leveluptogethermvp.userservice.home.api.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.global.enums.BannerType;
import io.pinkspider.global.enums.LinkType;
import io.pinkspider.global.feign.admin.AdminBannerDto;
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
public class HomeBannerResponse {

    private Long id;
    private BannerType bannerType;
    private String bannerTypeDisplayName;
    private String title;
    private String description;
    private String imageUrl;
    private LinkType linkType;
    private String linkUrl;
    private Long guildId;
    private Integer sortOrder;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private LocalDateTime createdAt;

    public static HomeBannerResponse from(AdminBannerDto dto) {
        BannerType type = BannerType.valueOf(dto.bannerType());
        return HomeBannerResponse.builder()
            .id(dto.id())
            .bannerType(type)
            .bannerTypeDisplayName(type.getDisplayName())
            .title(dto.title())
            .description(dto.description())
            .imageUrl(dto.imageUrl())
            .linkType(dto.linkType() != null ? LinkType.valueOf(dto.linkType()) : null)
            .linkUrl(dto.linkUrl())
            .guildId(dto.guildId())
            .sortOrder(dto.sortOrder())
            .startAt(dto.startAt())
            .endAt(dto.endAt())
            .createdAt(dto.createdAt())
            .build();
    }
}
