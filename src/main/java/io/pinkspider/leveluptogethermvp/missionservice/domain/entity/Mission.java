package io.pinkspider.leveluptogethermvp.missionservice.domain.entity;

import io.pinkspider.global.domain.auditentity.LocalDateTimeBaseEntity;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionStatus;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionType;
import io.pinkspider.leveluptogethermvp.missionservice.domain.enums.MissionVisibility;
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
import java.time.LocalDateTime;
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
@Table(name = "mission")
@Comment("미션")
public class Mission extends LocalDateTimeBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    @Comment("미션 ID")
    private Long id;

    @NotNull
    @Size(max = 200)
    @Column(name = "title", nullable = false, length = 200)
    @Comment("미션 제목")
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    @Comment("미션 설명")
    private String description;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Comment("미션 상태")
    private MissionStatus status;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", nullable = false, length = 20)
    @Comment("공개 여부")
    private MissionVisibility visibility;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    @Comment("미션 타입 (개인/길드)")
    private MissionType type;

    @NotNull
    @Column(name = "creator_id", nullable = false)
    @Comment("생성자 ID")
    private String creatorId;

    @Column(name = "guild_id")
    @Comment("길드 ID (길드 미션인 경우)")
    private String guildId;

    @Column(name = "max_participants")
    @Comment("최대 참여 인원")
    private Integer maxParticipants;

    @Column(name = "start_date")
    @Comment("미션 시작일")
    private LocalDateTime startDate;

    @Column(name = "end_date")
    @Comment("미션 종료일")
    private LocalDateTime endDate;

    @Builder.Default
    @OneToMany(mappedBy = "mission")
    private List<MissionParticipant> participants = new ArrayList<>();

    public void updateStatus(MissionStatus newStatus) {
        this.status = newStatus;
    }

    public void open() {
        if (this.status != MissionStatus.DRAFT) {
            throw new IllegalStateException("작성중 상태의 미션만 오픈할 수 있습니다.");
        }
        this.status = MissionStatus.OPEN;
    }

    public void start() {
        if (this.status != MissionStatus.OPEN) {
            throw new IllegalStateException("모집중 상태의 미션만 시작할 수 있습니다.");
        }
        this.status = MissionStatus.IN_PROGRESS;
    }

    public void complete() {
        if (this.status != MissionStatus.IN_PROGRESS) {
            throw new IllegalStateException("진행중 상태의 미션만 완료할 수 있습니다.");
        }
        this.status = MissionStatus.COMPLETED;
    }

    public void cancel() {
        if (this.status == MissionStatus.COMPLETED) {
            throw new IllegalStateException("완료된 미션은 취소할 수 없습니다.");
        }
        this.status = MissionStatus.CANCELLED;
    }

    public boolean isGuildMission() {
        return this.type == MissionType.GUILD;
    }

    public boolean isPublic() {
        return this.visibility == MissionVisibility.PUBLIC;
    }
}
