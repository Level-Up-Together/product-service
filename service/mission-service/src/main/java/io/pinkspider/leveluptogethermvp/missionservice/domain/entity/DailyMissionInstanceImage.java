package io.pinkspider.leveluptogethermvp.missionservice.domain.entity;

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
 * 고정 미션 일일 인스턴스 이미지 (QA-53). 인스턴스당 최대 5장.
 */
@Entity
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(name = "daily_mission_instance_image",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_daily_mission_instance_image_sort",
        columnNames = {"instance_id", "sort_order"}
    ),
    indexes = {
        @Index(name = "idx_daily_mission_instance_image_instance", columnList = "instance_id")
    })
@Comment("고정 미션 일일 인스턴스 이미지")
public class DailyMissionInstanceImage extends LocalDateTimeBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "instance_id", nullable = false)
    @Comment("일일 미션 인스턴스")
    private DailyMissionInstance instance;

    @NotNull
    @Column(name = "image_url", nullable = false, length = 500)
    @Comment("이미지 URL")
    private String imageUrl;

    @NotNull
    @Column(name = "sort_order", nullable = false)
    @Comment("노출 순서 (0=대표)")
    private Integer sortOrder;
}
