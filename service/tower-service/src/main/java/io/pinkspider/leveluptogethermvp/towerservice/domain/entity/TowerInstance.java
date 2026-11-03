package io.pinkspider.leveluptogethermvp.towerservice.domain.entity;

import io.pinkspider.global.domain.auditentity.LocalDateTimeBaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;

/**
 * LUT-630: 한 층의 인스턴스(방). 상한(층 {@code instance_capacity})을 넘으면 같은 층에 새 방을 연다. 자리 유지 중인 유저도 없고 아무도
 * 없으면 바로 닫는다(행 삭제). 서버는 유저 의사 없이 방을 합치거나 옮기지 않는다(자동 착석자의 자리 양보만 예외).
 */
@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(name = "tower_instance")
@Comment("레벨업 탑 인스턴스(방)")
public class TowerInstance extends LocalDateTimeBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "floor_number", nullable = false)
    private Integer floorNumber;

    @Column(name = "opened_at", nullable = false)
    private LocalDateTime openedAt;

    public static TowerInstance open(int floorNumber, LocalDateTime now) {
        return TowerInstance.builder().floorNumber(floorNumber).openedAt(now).build();
    }
}
