package io.pinkspider.leveluptogethermvp.feedservice.domain.entity;

import io.pinkspider.global.domain.auditentity.LocalDateTimeBaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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

/**
 * 활동 피드 이미지 (QA-53). 미션 공유 피드의 다중 이미지 캐러셀 노출용.
 * sort_order=0 이미지는 activity_feed.image_url 과 동기화.
 */
@Entity
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(name = "activity_feed_image",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_activity_feed_image_sort",
        columnNames = {"feed_id", "sort_order"}
    ),
    indexes = {
        @Index(name = "idx_activity_feed_image_feed", columnList = "feed_id")
    })
@Comment("활동 피드 이미지")
public class ActivityFeedImage extends LocalDateTimeBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "feed_id", nullable = false)
    @Comment("피드")
    private ActivityFeed feed;

    @NotNull
    @Column(name = "image_url", nullable = false, length = 500)
    @Comment("이미지 URL")
    private String imageUrl;

    @NotNull
    @Column(name = "sort_order", nullable = false)
    @Comment("캐러셀 노출 순서 (0=대표)")
    private Integer sortOrder;
}
