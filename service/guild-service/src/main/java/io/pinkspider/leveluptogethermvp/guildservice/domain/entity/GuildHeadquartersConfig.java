package io.pinkspider.leveluptogethermvp.guildservice.domain.entity;

import io.pinkspider.global.domain.auditentity.LocalDateTimeBaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Comment;

/**
 * 길드 거점 설정 (반경 등)
 */
@Entity
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(name = "guild_headquarters_config")
@Comment("길드 거점 설정")
public class GuildHeadquartersConfig extends LocalDateTimeBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    @Comment("ID")
    private Long id;

    @NotNull
    @Column(name = "base_radius_meters", nullable = false)
    @Comment("기본 거점 반경 (미터)")
    @Builder.Default
    private Integer baseRadiusMeters = 100;

    @NotNull
    @Column(name = "radius_increase_per_level_tier", nullable = false)
    @Comment("레벨 구간(10레벨)당 반경 증가량 (미터)")
    @Builder.Default
    private Integer radiusIncreasePerLevelTier = 20;

    @NotNull
    @Column(name = "level_tier_size", nullable = false)
    @Comment("레벨 구간 크기 (예: 10이면 10레벨마다 증가)")
    @Builder.Default
    private Integer levelTierSize = 10;

    @NotNull
    @Column(name = "is_active", nullable = false)
    @Comment("활성 여부")
    @Builder.Default
    private Boolean isActive = true;

    /**
     * 길드 레벨에 따른 보호 반경 계산
     * 공식: 기본 반경 + (레벨 / 레벨구간크기) * 구간당 증가량
     * 예: 레벨 25 -> 100 + (25 / 10) * 20 = 100 + 2 * 20 = 140m
     */
    public int calculateProtectionRadius(int guildLevel) {
        int levelTier = guildLevel / levelTierSize;
        return baseRadiusMeters + (levelTier * radiusIncreasePerLevelTier);
    }
}
