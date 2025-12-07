package io.pinkspider.leveluptogethermvp.userservice.attendance.domain.entity;

import io.pinkspider.global.domain.auditentity.LocalDateTimeBaseEntity;
import io.pinkspider.leveluptogethermvp.userservice.attendance.domain.enums.AttendanceRewardType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
@Table(name = "attendance_reward_config")
@Comment("출석 보상 설정")
public class AttendanceRewardConfig extends LocalDateTimeBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    @Comment("ID")
    private Long id;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "reward_type", nullable = false, length = 30)
    @Comment("보상 타입")
    private AttendanceRewardType rewardType;

    @Column(name = "required_days")
    @Comment("필요 일수 (연속 출석 보상 등)")
    private Integer requiredDays;

    @Column(name = "reward_exp")
    @Comment("보상 경험치")
    @Builder.Default
    private Integer rewardExp = 0;

    @Column(name = "reward_points")
    @Comment("보상 포인트")
    @Builder.Default
    private Integer rewardPoints = 0;

    @Column(name = "reward_title_id")
    @Comment("보상 칭호 ID")
    private Long rewardTitleId;

    @Column(name = "description", length = 200)
    @Comment("보상 설명")
    private String description;

    @Column(name = "is_active")
    @Comment("활성화 여부")
    @Builder.Default
    private Boolean isActive = true;
}
