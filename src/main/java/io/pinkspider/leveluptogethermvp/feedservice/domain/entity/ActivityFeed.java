package io.pinkspider.leveluptogethermvp.feedservice.domain.entity;

import io.pinkspider.global.domain.auditentity.LocalDateTimeBaseEntity;
import io.pinkspider.leveluptogethermvp.userservice.achievement.domain.enums.TitleRarity;
import io.pinkspider.leveluptogethermvp.feedservice.domain.enums.ActivityType;
import io.pinkspider.leveluptogethermvp.feedservice.domain.enums.FeedVisibility;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
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
@Table(name = "activity_feed",
    indexes = {
        @Index(name = "idx_feed_user", columnList = "user_id"),
        @Index(name = "idx_feed_created", columnList = "created_at DESC"),
        @Index(name = "idx_feed_visibility", columnList = "visibility"),
        @Index(name = "idx_feed_guild", columnList = "guild_id"),
        @Index(name = "idx_feed_category", columnList = "category_id"),
        @Index(name = "idx_feed_mission", columnList = "mission_id")
    })
@Comment("활동 피드")
public class ActivityFeed extends LocalDateTimeBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    @Comment("ID")
    private Long id;

    @NotNull
    @Column(name = "user_id", nullable = false)
    @Comment("사용자 ID")
    private String userId;

    @Column(name = "user_nickname", length = 50)
    @Comment("사용자 닉네임")
    private String userNickname;

    @Column(name = "user_profile_image_url", length = 500)
    @Comment("사용자 프로필 이미지")
    private String userProfileImageUrl;

    @Column(name = "user_level")
    @Comment("사용자 레벨 (피드 생성 시점)")
    @Builder.Default
    private Integer userLevel = 1;

    @Column(name = "user_title", length = 100)
    @Comment("사용자 칭호 (피드 생성 시점)")
    private String userTitle;

    @Enumerated(EnumType.STRING)
    @Column(name = "user_title_rarity", length = 20)
    @Comment("사용자 칭호 등급 (피드 생성 시점)")
    private TitleRarity userTitleRarity;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "activity_type", nullable = false, length = 30)
    @Comment("활동 타입")
    private ActivityType activityType;

    @NotNull
    @Column(name = "title", nullable = false, length = 100)
    @Comment("피드 제목")
    private String title;

    @Column(name = "description", length = 500)
    @Comment("피드 설명")
    private String description;

    @Column(name = "reference_type", length = 30)
    @Comment("참조 타입")
    private String referenceType;

    @Column(name = "reference_id")
    @Comment("참조 ID")
    private Long referenceId;

    @Column(name = "reference_name", length = 100)
    @Comment("참조 이름")
    private String referenceName;

    @Column(name = "guild_id")
    @Comment("관련 길드 ID")
    private Long guildId;

    @Column(name = "category_id")
    @Comment("미션 카테고리 ID")
    private Long categoryId;

    @Column(name = "image_url", length = 500)
    @Comment("이미지 URL")
    private String imageUrl;

    @Column(name = "icon_url", length = 500)
    @Comment("아이콘 URL")
    private String iconUrl;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", nullable = false, length = 20)
    @Comment("공개 범위")
    @Builder.Default
    private FeedVisibility visibility = FeedVisibility.PUBLIC;

    @Column(name = "like_count")
    @Comment("좋아요 수")
    @Builder.Default
    private Integer likeCount = 0;

    @Column(name = "comment_count")
    @Comment("댓글 수")
    @Builder.Default
    private Integer commentCount = 0;

    @Column(name = "mission_id")
    @Comment("미션 ID (미션 공유 피드인 경우)")
    private Long missionId;

    @Column(name = "execution_id")
    @Comment("미션 실행 ID (사용자 공유 피드인 경우)")
    private Long executionId;

    @Column(name = "duration_minutes")
    @Comment("미션 수행 시간 (분)")
    private Integer durationMinutes;

    @Column(name = "exp_earned")
    @Comment("획득 경험치")
    private Integer expEarned;

    public void incrementLikeCount() {
        this.likeCount++;
    }

    public void decrementLikeCount() {
        if (this.likeCount > 0) {
            this.likeCount--;
        }
    }

    public void incrementCommentCount() {
        this.commentCount++;
    }

    public void decrementCommentCount() {
        if (this.commentCount > 0) {
            this.commentCount--;
        }
    }

    public String getCategory() {
        return activityType.getCategory();
    }
}
