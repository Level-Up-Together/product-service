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
 * 미션 실행 이미지 (QA-53). 한 실행당 최대 5장. sort_order 로 노출 순서 보존.
 */
@Entity
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(name = "mission_execution_image",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_mission_execution_image_sort",
        columnNames = {"execution_id", "sort_order"}
    ),
    indexes = {
        @Index(name = "idx_mission_execution_image_execution", columnList = "execution_id")
    })
@Comment("미션 실행 이미지")
public class MissionExecutionImage extends LocalDateTimeBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "execution_id", nullable = false)
    @Comment("미션 실행")
    private MissionExecution execution;

    @NotNull
    @Column(name = "image_url", nullable = false, length = 500)
    @Comment("이미지 URL")
    private String imageUrl;

    @NotNull
    @Column(name = "sort_order", nullable = false)
    @Comment("노출 순서 (0=대표, mission_execution.image_url 과 동기화)")
    private Integer sortOrder;
}
