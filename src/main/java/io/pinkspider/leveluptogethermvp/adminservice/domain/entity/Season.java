package io.pinkspider.leveluptogethermvp.adminservice.domain.entity;

import io.pinkspider.global.domain.auditentity.LocalDateTimeBaseEntity;
import io.pinkspider.leveluptogethermvp.adminservice.domain.enums.SeasonStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Comment;

import java.time.LocalDateTime;

@Entity
@Table(name = "season")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Comment("시즌 테이블")
public class Season extends LocalDateTimeBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    @Comment("시즌 ID")
    private Long id;

    @Column(name = "title", nullable = false, length = 100)
    @Comment("시즌 타이틀")
    private String title;

    @Column(name = "description", length = 500)
    @Comment("시즌 설명")
    private String description;

    @Column(name = "start_at", nullable = false)
    @Comment("시즌 시작 일시")
    private LocalDateTime startAt;

    @Column(name = "end_at", nullable = false)
    @Comment("시즌 종료 일시")
    private LocalDateTime endAt;

    @Column(name = "is_active", nullable = false)
    @Comment("활성화 여부")
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "reward_title_id")
    @Comment("시즌 보상 칭호 ID")
    private Long rewardTitleId;

    @Column(name = "reward_title_name", length = 50)
    @Comment("시즌 보상 칭호 이름")
    private String rewardTitleName;

    @Column(name = "sort_order", nullable = false)
    @Comment("정렬 순서")
    @Builder.Default
    private Integer sortOrder = 0;

    @Column(name = "created_by", length = 100)
    @Comment("생성자")
    private String createdBy;

    @Column(name = "modified_by", length = 100)
    @Comment("수정자")
    private String modifiedBy;

    /**
     * 현재 시즌인지 확인
     */
    public boolean isCurrent() {
        if (!isActive) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        return !now.isBefore(startAt) && !now.isAfter(endAt);
    }

    /**
     * 시즌 상태 반환
     */
    public SeasonStatus getStatus() {
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(startAt)) {
            return SeasonStatus.UPCOMING;
        }
        if (now.isAfter(endAt)) {
            return SeasonStatus.ENDED;
        }
        return SeasonStatus.ACTIVE;
    }
}
