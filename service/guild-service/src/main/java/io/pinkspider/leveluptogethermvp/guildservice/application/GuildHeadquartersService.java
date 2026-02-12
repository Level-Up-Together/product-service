package io.pinkspider.leveluptogethermvp.guildservice.application;

import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildHeadquartersInfoResponse;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildHeadquartersInfoResponse.GuildHeadquartersInfo;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildHeadquartersInfoResponse.HeadquartersConfig;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildHeadquartersValidationResponse;
import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildHeadquartersValidationResponse.NearbyGuildInfo;
import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.Guild;
import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.GuildHeadquartersConfig;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildHeadquartersConfigRepository;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildRepository;
import io.pinkspider.leveluptogethermvp.metaservice.application.MissionCategoryService;
import io.pinkspider.leveluptogethermvp.metaservice.domain.dto.MissionCategoryResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GuildHeadquartersService {

    private final GuildRepository guildRepository;
    private final GuildHeadquartersConfigRepository configRepository;
    private final MissionCategoryService missionCategoryService;

    // 지구 반지름 (미터)
    private static final double EARTH_RADIUS_METERS = 6371000.0;

    // 기본 설정값 (DB에 설정이 없을 경우)
    private static final int DEFAULT_BASE_RADIUS = 100;
    private static final int DEFAULT_RADIUS_INCREASE = 20;
    private static final int DEFAULT_LEVEL_TIER_SIZE = 10;

    /**
     * 거점 설정 가능 여부 검증
     * @param guildId 내 길드 ID (자신의 길드는 제외)
     * @param latitude 설정하려는 위도
     * @param longitude 설정하려는 경도
     */
    public GuildHeadquartersValidationResponse validateHeadquartersLocation(
            Long guildId, double latitude, double longitude) {

        GuildHeadquartersConfig config = getActiveConfig();
        List<Guild> guildsWithHq = guildId != null
                ? guildRepository.findAllWithHeadquartersExcluding(guildId)
                : guildRepository.findAllWithHeadquarters();

        List<NearbyGuildInfo> conflictingGuilds = new ArrayList<>();

        for (Guild guild : guildsWithHq) {
            int protectionRadius = config.calculateProtectionRadius(guild.getCurrentLevel());
            double distance = calculateDistance(
                    latitude, longitude,
                    guild.getBaseLatitude(), guild.getBaseLongitude()
            );

            if (distance < protectionRadius) {
                conflictingGuilds.add(NearbyGuildInfo.builder()
                        .guildId(guild.getId())
                        .guildName(guild.getName())
                        .guildLevel(guild.getCurrentLevel())
                        .latitude(guild.getBaseLatitude())
                        .longitude(guild.getBaseLongitude())
                        .protectionRadiusMeters(protectionRadius)
                        .distanceMeters(Math.round(distance * 100.0) / 100.0)
                        .build());
            }
        }

        boolean isValid = conflictingGuilds.isEmpty();
        String message = isValid
                ? "해당 위치에 거점을 설정할 수 있습니다."
                : "다른 길드의 거점 보호 구역 내에 있어 설정할 수 없습니다.";

        return GuildHeadquartersValidationResponse.builder()
                .valid(isValid)
                .message(message)
                .nearbyGuilds(conflictingGuilds)
                .baseRadiusMeters(config.getBaseRadiusMeters())
                .radiusIncreasePerLevelTier(config.getRadiusIncreasePerLevelTier())
                .levelTierSize(config.getLevelTierSize())
                .build();
    }

    /**
     * 모든 길드의 거점 정보 조회 (지도 표시용)
     */
    public GuildHeadquartersInfoResponse getAllHeadquartersInfo() {
        GuildHeadquartersConfig config = getActiveConfig();
        List<Guild> guildsWithHq = guildRepository.findAllWithHeadquarters();

        // 카테고리 정보 조회
        List<Long> categoryIds = guildsWithHq.stream()
                .map(Guild::getCategoryId)
                .filter(id -> id != null)
                .distinct()
                .toList();

        Map<Long, MissionCategoryResponse> categoryMap = categoryIds.stream()
                .collect(Collectors.toMap(
                        id -> id,
                        id -> {
                            try {
                                return missionCategoryService.getCategory(id);
                            } catch (Exception e) {
                                return null;
                            }
                        },
                        (existing, replacement) -> existing
                ));

        List<GuildHeadquartersInfo> guildInfoList = guildsWithHq.stream()
                .map(guild -> {
                    int protectionRadius = config.calculateProtectionRadius(guild.getCurrentLevel());
                    MissionCategoryResponse category = categoryMap.get(guild.getCategoryId());

                    return GuildHeadquartersInfo.builder()
                            .guildId(guild.getId())
                            .guildName(guild.getName())
                            .guildImageUrl(guild.getImageUrl())
                            .guildLevel(guild.getCurrentLevel())
                            .categoryId(guild.getCategoryId())
                            .categoryName(category != null ? category.getName() : null)
                            .categoryIcon(category != null ? category.getIcon() : null)
                            .latitude(guild.getBaseLatitude())
                            .longitude(guild.getBaseLongitude())
                            .protectionRadiusMeters(protectionRadius)
                            .build();
                })
                .toList();

        return GuildHeadquartersInfoResponse.builder()
                .guilds(guildInfoList)
                .config(HeadquartersConfig.builder()
                        .baseRadiusMeters(config.getBaseRadiusMeters())
                        .radiusIncreasePerLevelTier(config.getRadiusIncreasePerLevelTier())
                        .levelTierSize(config.getLevelTierSize())
                        .build())
                .build();
    }

    /**
     * 거점 설정 시 검증 (GuildService에서 사용)
     * @throws IllegalStateException 설정 불가 위치인 경우
     */
    public void validateAndThrowIfInvalid(Long guildId, double latitude, double longitude) {
        GuildHeadquartersValidationResponse validation = validateHeadquartersLocation(guildId, latitude, longitude);

        if (!validation.isValid()) {
            NearbyGuildInfo nearestGuild = validation.getNearbyGuilds().get(0);
            throw new IllegalStateException(String.format(
                    "'%s' 길드(Lv.%d)의 거점 보호 구역 내입니다. (거리: %.0fm, 보호 반경: %dm)",
                    nearestGuild.getGuildName(),
                    nearestGuild.getGuildLevel(),
                    nearestGuild.getDistanceMeters(),
                    nearestGuild.getProtectionRadiusMeters()
            ));
        }
    }

    /**
     * 활성 설정 조회 (없으면 기본값)
     */
    private GuildHeadquartersConfig getActiveConfig() {
        return configRepository.findActiveConfig()
                .orElseGet(() -> GuildHeadquartersConfig.builder()
                        .baseRadiusMeters(DEFAULT_BASE_RADIUS)
                        .radiusIncreasePerLevelTier(DEFAULT_RADIUS_INCREASE)
                        .levelTierSize(DEFAULT_LEVEL_TIER_SIZE)
                        .build());
    }

    /**
     * Haversine 공식을 사용한 두 지점 간 거리 계산 (미터)
     */
    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        double lat1Rad = Math.toRadians(lat1);
        double lat2Rad = Math.toRadians(lat2);
        double deltaLat = Math.toRadians(lat2 - lat1);
        double deltaLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
                + Math.cos(lat1Rad) * Math.cos(lat2Rad)
                * Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return EARTH_RADIUS_METERS * c;
    }
}
