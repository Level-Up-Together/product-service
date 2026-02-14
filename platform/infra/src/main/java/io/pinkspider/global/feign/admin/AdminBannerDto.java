package io.pinkspider.global.feign.admin;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.LocalDateTime;

@JsonNaming(SnakeCaseStrategy.class)
public record AdminBannerDto(
    Long id,
    String bannerType,
    String title,
    String description,
    String imageUrl,
    String linkType,
    String linkUrl,
    Long guildId,
    Integer sortOrder,
    Boolean isActive,
    LocalDateTime startAt,
    LocalDateTime endAt,
    LocalDateTime createdAt,
    LocalDateTime modifiedAt
) {}
