package io.pinkspider.leveluptogethermvp.userservice.home.api.dto;

import io.pinkspider.leveluptogethermvp.userservice.home.domain.entity.HomeBanner;
import io.pinkspider.leveluptogethermvp.userservice.home.domain.enums.BannerType;
import io.pinkspider.leveluptogethermvp.userservice.home.domain.enums.LinkType;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
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
    private LocalDateTime startDate;
    private LocalDateTime endDate;
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
            .startDate(banner.getStartDate())
            .endDate(banner.getEndDate())
            .createdAt(banner.getCreatedAt())
            .build();
    }
}
