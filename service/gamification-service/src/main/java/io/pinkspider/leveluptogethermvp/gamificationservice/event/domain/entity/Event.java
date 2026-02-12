package io.pinkspider.leveluptogethermvp.gamificationservice.event.domain.entity;

import io.pinkspider.global.domain.auditentity.LocalDateTimeBaseEntity;
import io.pinkspider.global.translation.LocaleUtils;
import io.pinkspider.leveluptogethermvp.gamificationservice.event.domain.enums.EventStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Comment;

@Entity
@Table(name = "event")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class Event extends LocalDateTimeBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("이벤트 ID")
    private Long id;

    @Column(name = "name", nullable = false, length = 100)
    @Comment("이벤트명")
    private String name;

    @Column(name = "name_en", length = 100)
    @Comment("이벤트명 (영어)")
    private String nameEn;

    @Column(name = "name_ar", length = 100)
    @Comment("이벤트명 (아랍어)")
    private String nameAr;

    @Column(name = "description", columnDefinition = "TEXT")
    @Comment("설명")
    private String description;

    @Column(name = "description_en", columnDefinition = "TEXT")
    @Comment("설명 (영어)")
    private String descriptionEn;

    @Column(name = "description_ar", columnDefinition = "TEXT")
    @Comment("설명 (아랍어)")
    private String descriptionAr;

    @Column(name = "image_url", length = 500)
    @Comment("이벤트 이미지 URL")
    private String imageUrl;

    @Column(name = "start_at", nullable = false)
    @Comment("시작일시")
    private LocalDateTime startAt;

    @Column(name = "end_at", nullable = false)
    @Comment("종료일시")
    private LocalDateTime endAt;

    @Column(name = "reward_title_id")
    @Comment("보상 칭호 ID")
    private Long rewardTitleId;

    @Column(name = "reward_title_name", length = 100)
    @Comment("보상 칭호명 (조회용)")
    private String rewardTitleName;

    @Column(name = "is_active", nullable = false)
    @Comment("활성화 여부")
    @Builder.Default
    private Boolean isActive = true;

    /**
     * 현재 진행중인 이벤트인지 확인
     */
    public boolean isCurrent() {
        if (!isActive) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        return !now.isBefore(startAt) && !now.isAfter(endAt);
    }

    /**
     * 이벤트 상태 반환
     */
    public EventStatus getStatus() {
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(startAt)) {
            return EventStatus.SCHEDULED;
        }
        if (now.isAfter(endAt)) {
            return EventStatus.ENDED;
        }
        return EventStatus.IN_PROGRESS;
    }

    /**
     * locale에 따라 이벤트명을 반환합니다.
     */
    public String getLocalizedName(String locale) {
        return LocaleUtils.getLocalizedText(name, nameEn, nameAr, locale);
    }

    /**
     * locale에 따라 설명을 반환합니다.
     */
    public String getLocalizedDescription(String locale) {
        return LocaleUtils.getLocalizedText(description, descriptionEn, descriptionAr, locale);
    }
}
