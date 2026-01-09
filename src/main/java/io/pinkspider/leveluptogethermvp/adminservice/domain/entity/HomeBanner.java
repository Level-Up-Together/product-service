package io.pinkspider.leveluptogethermvp.adminservice.domain.entity;

import io.pinkspider.global.domain.auditentity.LocalDateTimeBaseEntity;
import io.pinkspider.leveluptogethermvp.adminservice.domain.enums.BannerType;
import io.pinkspider.leveluptogethermvp.adminservice.domain.enums.LinkType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
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
@Table(name = "home_banner")
@Comment("홈 배너")
public class HomeBanner extends LocalDateTimeBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    @Comment("배너 ID")
    private Long id;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "banner_type", nullable = false, length = 30)
    @Comment("배너 유형 (GUILD_RECRUIT, EVENT, NOTICE, AD)")
    private BannerType bannerType;

    @NotNull
    @Size(max = 100)
    @Column(name = "title", nullable = false, length = 100)
    @Comment("배너 제목")
    private String title;

    @Size(max = 500)
    @Column(name = "description", length = 500)
    @Comment("배너 설명")
    private String description;

    @Column(name = "image_url", length = 500)
    @Comment("배너 이미지 URL")
    private String imageUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "link_type", length = 30)
    @Comment("링크 유형 (GUILD, MISSION, EXTERNAL, INTERNAL)")
    private LinkType linkType;

    @Column(name = "link_url", length = 500)
    @Comment("링크 URL")
    private String linkUrl;

    @Column(name = "guild_id")
    @Comment("길드 모집 배너인 경우 연결된 길드 ID")
    private Long guildId;

    @Column(name = "sort_order")
    @Comment("정렬 순서")
    @Builder.Default
    private Integer sortOrder = 0;

    @Column(name = "is_active", nullable = false)
    @Comment("활성 여부")
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "start_at")
    @Comment("배너 시작일시")
    private LocalDateTime startAt;

    @Column(name = "end_at")
    @Comment("배너 종료일시")
    private LocalDateTime endAt;

    /**
     * 현재 배너가 표시 가능한지 확인
     */
    public boolean isDisplayable() {
        if (!isActive) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        if (startAt != null && now.isBefore(startAt)) {
            return false;
        }
        if (endAt != null && now.isAfter(endAt)) {
            return false;
        }
        return true;
    }
}
