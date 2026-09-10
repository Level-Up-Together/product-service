package io.pinkspider.leveluptogethermvp.guildservice.domain.entity;

import io.pinkspider.global.domain.auditentity.LocalDateTimeBaseEntity;
import io.pinkspider.leveluptogethermvp.guildservice.domain.enums.GuildJoinType;
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

    @Enumerated(EnumType.STRING)
    @Column(name = "join_type", nullable = false, length = 20)
    @Comment("가입 유형 (OPEN: 자동 가입, APPROVAL_REQUIRED: 승인 필요)")
    @Builder.Default
    private GuildJoinType joinType = GuildJoinType.OPEN;

    @NotNull
    @Column(name = "master_id", nullable = false)
    @Comment("길드 마스터 ID")
    private String masterId;

    @Column(name = "max_members")
    @Comment("최대 멤버 수")
    @Builder.Default
    private Integer maxMembers = 10;

    @Column(name = "image_url")
    @Comment("길드 이미지 URL")
    private String imageUrl;

    @Column(name = "is_active", nullable = false)
    @Comment("활성 여부")
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "is_banned", nullable = false)
    @Comment("신고 처리로 차단 여부")
    @Builder.Default
    private Boolean isBanned = false;

    @Column(name = "banned_at")
    @Comment("차단 시각")
    private java.time.LocalDateTime bannedAt;

    @Column(name = "banned_reason", length = 500)
    @Comment("차단 사유")
    private String bannedReason;

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

    // LUT-483: 길드 랭킹·레벨의 기준값. 길드원 개인의 일간 길드미션 EXP 를 10 단위 사다리
    // (상한 60 = 6점)로 점수화한 합의 누적. 단조 증가만 하며 회수 로직이 없다.
    @Column(name = "total_point", nullable = false)
    @Comment("총 누적 활동 포인트 (랭킹·레벨 기준)")
    @Builder.Default
    private Integer totalPoint = 0;

    @Column(name = "current_point", nullable = false)
    @Comment("현재 레벨에서의 포인트")
    @Builder.Default
    private Integer currentPoint = 0;

    @Column(name = "last_point_at")
    @Comment("마지막 포인트 적립 시각 (랭킹 동점 처리용 — 먼저 도달한 길드 우선)")
    private java.time.LocalDateTime lastPointAt;

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

    public boolean isOpenJoin() {
        return this.joinType == GuildJoinType.OPEN;
    }

    public boolean requiresApproval() {
        return this.joinType == GuildJoinType.APPROVAL_REQUIRED;
    }

    public boolean isMaster(String userId) {
        return this.masterId.equals(userId);
    }

    public void transferMaster(String newMasterId) {
        this.masterId = newMasterId;
    }

    /**
     * 신고 처리로 길드 차단. 후속 멤버/콘텐츠 처리는 운영자 수동.
     */
    public void banFromReport(String reason) {
        this.isBanned = true;
        this.bannedAt = java.time.LocalDateTime.now();
        this.bannedReason = reason;
    }

    public void deactivate() {
        this.isActive = false;
    }

    /** LUT-483: 포인트 적립 — 단조 증가만 한다 */
    public void addPoint(int point) {
        this.totalPoint += point;
        this.currentPoint += point;
        this.lastPointAt = java.time.LocalDateTime.now();
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
