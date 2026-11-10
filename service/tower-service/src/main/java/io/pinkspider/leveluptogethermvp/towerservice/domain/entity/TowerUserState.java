package io.pinkspider.leveluptogethermvp.towerservice.domain.entity;

import io.pinkspider.global.domain.auditentity.LocalDateTimeBaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;

/** LUT-629: 유저별 탑 상태 — 마지막으로 머문 층(기본 입장 층). 입장 API(LUT-630)가 갱신한다. */
@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(name = "tower_user_state")
@Comment("유저별 탑 상태")
public class TowerUserState extends LocalDateTimeBaseEntity {

    @Id
    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "last_floor_number", nullable = false)
    @Comment("마지막으로 머문 층 번호")
    private Integer lastFloorNumber;

    @Column(name = "last_entered_at", nullable = false)
    private LocalDateTime lastEnteredAt;

    public void visit(int floorNumber, LocalDateTime at) {
        this.lastFloorNumber = floorNumber;
        this.lastEnteredAt = at;
    }
}
