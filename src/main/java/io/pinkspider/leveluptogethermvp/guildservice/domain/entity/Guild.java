package io.pinkspider.leveluptogethermvp.guildservice.domain.entity;

import io.pinkspider.global.domain.auditentity.LocalDateTimeBaseEntity;
import io.pinkspider.leveluptogethermvp.guildservice.domain.enums.GuildVisibility;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;
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
@Table(name = "guild")
@Comment("길드")
public class Guild extends LocalDateTimeBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    @Comment("길드 ID")
    private Long id;

    @NotNull
    @Size(max = 100)
    @Column(name = "name", nullable = false, length = 100)
    @Comment("길드명")
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    @Comment("길드 설명")
    private String description;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", nullable = false, length = 20)
    @Comment("공개 여부")
    private GuildVisibility visibility;

    @NotNull
    @Column(name = "master_id", nullable = false)
    @Comment("길드 마스터 ID")
    private String masterId;

    @Column(name = "max_members")
    @Comment("최대 멤버 수")
    @Builder.Default
    private Integer maxMembers = 50;

    @Column(name = "image_url")
    @Comment("길드 이미지 URL")
    private String imageUrl;

    @Column(name = "is_active", nullable = false)
    @Comment("활성 여부")
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "current_level", nullable = false)
    @Comment("현재 길드 레벨")
    @Builder.Default
    private Integer currentLevel = 1;

    @Column(name = "current_exp", nullable = false)
    @Comment("현재 레벨에서의 경험치")
    @Builder.Default
    private Integer currentExp = 0;

    @Column(name = "total_exp", nullable = false)
    @Comment("총 누적 경험치")
    @Builder.Default
    private Integer totalExp = 0;

    @NotNull
    @Column(name = "category_id", nullable = false)
    @Comment("카테고리 ID (mission_category 참조)")
    private Long categoryId;

    @Column(name = "base_address")
    @Comment("거점 주소")
    private String baseAddress;

    @Column(name = "base_latitude")
    @Comment("거점 위도")
    private Double baseLatitude;

    @Column(name = "base_longitude")
    @Comment("거점 경도")
    private Double baseLongitude;

    @Builder.Default
    @OneToMany(mappedBy = "guild")
    private List<GuildMember> members = new ArrayList<>();

    public boolean isPublic() {
        return this.visibility == GuildVisibility.PUBLIC;
    }

    public boolean isPrivate() {
        return this.visibility == GuildVisibility.PRIVATE;
    }

    public boolean isMaster(String userId) {
        return this.masterId.equals(userId);
    }

    public void transferMaster(String newMasterId) {
        this.masterId = newMasterId;
    }

    public void deactivate() {
        this.isActive = false;
    }

    public void addExperience(int exp) {
        this.currentExp += exp;
        this.totalExp += exp;
    }

    public void levelUp(int requiredExp) {
        this.currentExp -= requiredExp;
        this.currentLevel++;
    }

    public void updateMaxMembersByLevel(int newMaxMembers) {
        this.maxMembers = newMaxMembers;
    }
}
