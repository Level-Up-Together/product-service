package io.pinkspider.leveluptogethermvp.guildservice.application;

import io.pinkspider.leveluptogethermvp.guildservice.domain.dto.GuildResponse;
import io.pinkspider.leveluptogethermvp.guildservice.domain.entity.Guild;
import io.pinkspider.leveluptogethermvp.guildservice.infrastructure.GuildRepository;
import io.pinkspider.leveluptogethermvp.metaservice.application.MissionCategoryService;
import io.pinkspider.leveluptogethermvp.metaservice.domain.dto.MissionCategoryResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class GuildHelper {

    private final GuildRepository guildRepository;
    private final MissionCategoryService missionCategoryService;

    public Guild findActiveGuildById(Long guildId) {
        return guildRepository.findByIdAndIsActiveTrue(guildId)
            .orElseThrow(() -> new IllegalArgumentException("길드를 찾을 수 없습니다: " + guildId));
    }

    public void validateMaster(Guild guild, String userId) {
        if (!guild.isMaster(userId)) {
            throw new IllegalStateException("길드 마스터만 이 작업을 수행할 수 있습니다.");
        }
    }

    public GuildResponse buildGuildResponseWithCategory(Guild guild, int memberCount) {
        String categoryName = null;
        String categoryIcon = null;

        if (guild.getCategoryId() != null) {
            try {
                MissionCategoryResponse category = missionCategoryService.getCategory(guild.getCategoryId());
                if (category != null) {
                    categoryName = category.getName();
                    categoryIcon = category.getIcon();
                }
            } catch (Exception e) {
                log.warn("카테고리 조회 실패: categoryId={}", guild.getCategoryId(), e);
            }
        }

        return GuildResponse.from(guild, memberCount, categoryName, categoryIcon);
    }
}
