package io.pinkspider.leveluptogethermvp.guildservice.domain.entity;

import io.pinkspider.global.domain.auditentity.CreatedAtEntity;
import io.pinkspider.global.enums.GuildExpSourceType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
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
@Table(name = "guild_experience_history")
@Comment("길드 경험치 히스토리")
public class GuildExperienceHistory extends CreatedAtEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    @Comment("ID")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "guild_id", nullable = false)
    @Comment("길드")
    private Guild guild;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 50)
    @Comment("경험치 획득 유형")
    private GuildExpSourceType sourceType;

    @Column(name = "source_id")
    @Comment("경험치 획득 소스 ID")
    private Long sourceId;

    @Column(name = "contributor_id")
    @Comment("기여자 ID (경험치를 획득해준 길드원)")
    private String contributorId;

    @NotNull
    @Column(name = "exp_amount", nullable = false)
    @Comment("획득 경험치")
    private Integer expAmount;

    @Column(name = "description", length = 500)
    @Comment("설명")
    private String description;

    @Column(name = "level_before")
    @Comment("획득 전 레벨")
    private Integer levelBefore;

    @Column(name = "level_after")
    @Comment("획득 후 레벨")
    private Integer levelAfter;
}
