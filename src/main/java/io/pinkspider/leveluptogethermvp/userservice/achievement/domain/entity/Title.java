package io.pinkspider.leveluptogethermvp.userservice.achievement.domain.entity;

import io.pinkspider.global.domain.auditentity.LocalDateTimeBaseEntity;
import io.pinkspider.leveluptogethermvp.userservice.achievement.domain.enums.TitleRarity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

@Entity
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(name = "title")
@Comment("칭호")
public class Title extends LocalDateTimeBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    @Comment("칭호 ID")
    private Long id;

    @NotNull
    @Column(name = "name", nullable = false, length = 50)
    @Comment("칭호 이름")
    private String name;

    @Column(name = "description", length = 200)
    @Comment("칭호 설명")
    private String description;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "rarity", nullable = false, length = 20)
    @Comment("칭호 등급")
    private TitleRarity rarity;

    @Column(name = "prefix", length = 20)
    @Comment("접두사 (예: [전설의])")
    private String prefix;

    @Column(name = "suffix", length = 20)
    @Comment("접미사 (예: ~의 수호자)")
    private String suffix;

    @Column(name = "color_code", length = 10)
    @Comment("색상 코드")
    private String colorCode;

    @Column(name = "icon_url")
    @Comment("칭호 아이콘 URL")
    private String iconUrl;

    @Column(name = "mission_category_id")
    @Comment("미션 카테고리 ID")
    private Long missionCategoryId;

    @NotNull
    @Column(name = "is_active", nullable = false)
    @Comment("활성 여부")
    @Builder.Default
    private Boolean isActive = true;

    public String getDisplayName() {
        StringBuilder sb = new StringBuilder();
        if (prefix != null) {
            sb.append(prefix).append(" ");
        }
        sb.append(name);
        if (suffix != null) {
            sb.append(" ").append(suffix);
        }
        return sb.toString().trim();
    }
}
