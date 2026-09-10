package io.pinkspider.leveluptogethermvp.metaservice.guildlevelconfig.domain.entity;

import io.pinkspider.global.domain.auditentity.LocalDateTimeBaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Comment;

@Entity
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(name = "guild_level_config",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_guild_level_config_level",
        columnNames = {"level"}
    )
)
@Comment("길드 레벨 설정")
public class GuildLevelConfig extends LocalDateTimeBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    @Comment("ID")
    private Long id;

    @NotNull
    @Column(name = "level", nullable = false)
    @Comment("길드 레벨")
    private Integer level;

    @NotNull
    @Column(name = "required_exp", nullable = false)
    @Comment("다음 레벨까지 필요한 경험치 (LUT-483 이후 미사용 — 포인트 기준으로 전환)")
    private Integer requiredExp;

    @Column(name = "cumulative_exp")
    @Comment("이 레벨까지 누적 필요 경험치 (LUT-483 이후 미사용)")
    private Integer cumulativeExp;

    // LUT-483: 길드 레벨 기준을 누적 EXP → 누적 활동 포인트로 전환.
    // 포인트는 EXP 보다 훨씬 작은 스케일(활성 5명이 매일 상한까지 채워도 일 30점)이라 별도 컬럼·값을 쓴다.
    @Column(name = "required_point")
    @Comment("다음 레벨까지 필요한 포인트")
    private Integer requiredPoint;

    @Column(name = "cumulative_point")
    @Comment("이 레벨까지 누적 필요 포인트 (레벨 판정 단일 기준)")
    private Integer cumulativePoint;

    @NotNull
    @Column(name = "max_members", nullable = false)
    @Comment("해당 레벨에서 최대 멤버 수")
    private Integer maxMembers;

    @Column(name = "title", length = 50)
    @Comment("길드 레벨 칭호")
    private String title;

    @Column(name = "description", length = 200)
    @Comment("레벨 설명")
    private String description;
}
