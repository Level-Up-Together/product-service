package io.pinkspider.leveluptogethermvp.towerservice.domain.entity;

import io.pinkspider.global.domain.auditentity.LocalDateTimeBaseEntity;
import io.pinkspider.leveluptogethermvp.towerservice.domain.enums.TowerOccupantKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;

/**
 * LUT-630: 인스턴스 점유자 한 명(로그인 유저 또는 방문객). 유저는 탑 전체에서 자리 하나({@code uk_tower_occupant_user}).
 *
 * <p>좌표·연결 여부 같은 실시간 presence 는 LUT-631(Redis) 몫이고, 이 표는 "누가 어느 방에 있는가"(배정·정원·인원 집계의 원천)만 든다.
 * {@code seat_x/seat_y} 는 입장 시점의 자리(입장 지점)다.
 */
@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(
        name = "tower_occupant",
        uniqueConstraints = {
            @UniqueConstraint(name = "uk_tower_occupant_user", columnNames = "user_id"),
            @UniqueConstraint(name = "uk_tower_occupant_visitor", columnNames = "visitor_key")
        })
@Comment("레벨업 탑 인스턴스 점유자")
public class TowerOccupant extends LocalDateTimeBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "instance_id", nullable = false)
    private Long instanceId;

    @Column(name = "floor_number", nullable = false)
    private Integer floorNumber;

    @Column(name = "user_id")
    @Comment("로그인 유저 ID (방문객은 NULL)")
    private String userId;

    @Column(name = "visitor_key", length = 64)
    @Comment("방문객 키 (로그인 유저는 NULL) — 퇴장 등 방문객 본인 확인용")
    private String visitorKey;

    /** LUT-631/705: 방문객 이름의 네 자리 숫자("방문객 4821") — 입장 때 서버가 임의로 준다 */
    @Column(name = "visitor_no")
    private Integer visitorNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 20)
    private TowerOccupantKind kind;

    @Column(name = "mission_active", nullable = false)
    @Comment("미션 중 — true 면 연결이 끊겨도 자리를 유지한다")
    @Builder.Default
    private Boolean missionActive = false;

    @Column(name = "seat_x", nullable = false)
    private Integer seatX;

    @Column(name = "seat_y", nullable = false)
    private Integer seatY;

    @Column(name = "entered_at", nullable = false)
    private LocalDateTime enteredAt;

    @Column(name = "last_active_at", nullable = false)
    private LocalDateTime lastActiveAt;

    public static TowerOccupant direct(
            Long instanceId,
            int floorNumber,
            String userId,
            boolean missionActive,
            int seatX,
            int seatY,
            LocalDateTime now) {
        return TowerOccupant.builder()
                .instanceId(instanceId)
                .floorNumber(floorNumber)
                .userId(userId)
                .kind(TowerOccupantKind.DIRECT)
                .missionActive(missionActive)
                .seatX(seatX)
                .seatY(seatY)
                .enteredAt(now)
                .lastActiveAt(now)
                .build();
    }

    public static TowerOccupant visitor(
            Long instanceId,
            int floorNumber,
            String visitorKey,
            int visitorNo,
            int seatX,
            int seatY,
            LocalDateTime now) {
        return TowerOccupant.builder()
                .instanceId(instanceId)
                .floorNumber(floorNumber)
                .visitorKey(visitorKey)
                .visitorNo(visitorNo)
                .kind(TowerOccupantKind.VISITOR)
                .missionActive(false)
                .seatX(seatX)
                .seatY(seatY)
                .enteredAt(now)
                .lastActiveAt(now)
                .build();
    }

    public static TowerOccupant autoSeated(
            Long instanceId,
            int floorNumber,
            String userId,
            int seatX,
            int seatY,
            LocalDateTime now) {
        return TowerOccupant.builder()
                .instanceId(instanceId)
                .floorNumber(floorNumber)
                .userId(userId)
                .kind(TowerOccupantKind.AUTO_SEATED)
                .missionActive(true)
                .seatX(seatX)
                .seatY(seatY)
                .enteredAt(now)
                .lastActiveAt(now)
                .build();
    }

    public boolean isMissionActive() {
        return Boolean.TRUE.equals(missionActive);
    }

    public boolean isAutoSeated() {
        return kind == TowerOccupantKind.AUTO_SEATED;
    }

    public boolean isVisitor() {
        return kind == TowerOccupantKind.VISITOR;
    }

    /** 자동 착석 자리에서 직접 입장으로 — 미션 중 상태는 그대로 */
    public void convertToDirect(LocalDateTime now) {
        this.kind = TowerOccupantKind.DIRECT;
        this.lastActiveAt = now;
    }

    public void startMission(LocalDateTime now) {
        this.missionActive = true;
        this.lastActiveAt = now;
    }

    public void endMission() {
        this.missionActive = false;
    }

    public void touch(LocalDateTime now) {
        this.lastActiveAt = now;
    }

    /** 같은 층의 다른 방으로 (자동 착석자 자리 양보에만 쓴다) */
    public void moveTo(Long instanceId) {
        this.instanceId = instanceId;
    }
}
