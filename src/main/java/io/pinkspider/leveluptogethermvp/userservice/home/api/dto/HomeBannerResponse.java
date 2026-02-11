package io.pinkspider.leveluptogethermvp.userservice.home.api.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.pinkspider.leveluptogethermvp.adminservice.domain.entity.HomeBanner;
import io.pinkspider.global.enums.BannerType;
import io.pinkspider.global.enums.LinkType;
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

    public static HomeBannerResponse from(HomeBanner banner) {
        return HomeBannerResponse.builder()
            .id(banner.getId())
            .bannerType(banner.getBannerType())
            .bannerTypeDisplayName(banner.getBannerType().getDisplayName())
            .title(banner.getTitle())
            .description(banner.getDescription())
            .imageUrl(banner.getImageUrl())
            .linkType(banner.getLinkType())
            .linkUrl(banner.getLinkUrl())
            .guildId(banner.getGuildId())
            .sortOrder(banner.getSortOrder())
            .startAt(banner.getStartAt())
            .endAt(banner.getEndAt())
            .createdAt(banner.getCreatedAt())
            .build();
    }
}
