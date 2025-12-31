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
import java.time.LocalDateTime;
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
@Table(name = "featured_feed",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_featured_feed",
        columnNames = {"category_id", "feed_id"}
    )
)
@Comment("추천 피드/미션")
public class FeaturedFeed extends LocalDateTimeBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    @Comment("ID")
    private Long id;

    @Column(name = "category_id")
    @Comment("카테고리 ID (NULL=전체, mission_category.id 참조)")
    private Long categoryId;

    @NotNull
    @Column(name = "feed_id", nullable = false)
    @Comment("피드 ID (activity_feed.id 참조)")
    private Long feedId;

    @NotNull
    @Column(name = "display_order", nullable = false)
    @Comment("표시 순서 (낮을수록 상위)")
    private Integer displayOrder;

    @NotNull
    @Column(name = "is_active", nullable = false)
    @Comment("활성화 여부")
    private Boolean isActive;

    @Column(name = "start_at")
    @Comment("노출 시작일시")
    private LocalDateTime startAt;

    @Column(name = "end_at")
    @Comment("노출 종료일시")
    private LocalDateTime endAt;

    @Column(name = "created_by", length = 100)
    @Comment("생성자")
    private String createdBy;

    @Column(name = "modified_by", length = 100)
    @Comment("수정자")
    private String modifiedBy;
}
