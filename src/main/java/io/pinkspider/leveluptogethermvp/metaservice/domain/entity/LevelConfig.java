package io.pinkspider.leveluptogethermvp.metaservice.domain.entity;

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
@Table(name = "level_config",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_level_config_level",
        columnNames = {"level"}
    )
)
@Comment("레벨 설정")
public class LevelConfig extends LocalDateTimeBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    @Comment("ID")
    private Long id;

    @NotNull
    @Column(name = "level", nullable = false)
    @Comment("레벨")
    private Integer level;

    @NotNull
    @Column(name = "required_exp", nullable = false)
    @Comment("다음 레벨까지 필요한 경험치")
    private Integer requiredExp;

    @Column(name = "cumulative_exp")
    @Comment("이 레벨까지 누적 필요 경험치")
    private Integer cumulativeExp;

    @Column(name = "title", length = 50)
    @Comment("레벨 칭호")
    private String title;

    @Column(name = "description", length = 200)
    @Comment("레벨 설명")
    private String description;
}
