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

    /** LUT-738: 방 번호 — 층 안에서 1부터, 열 때 비어 있는 가장 작은 번호. 옛 코드가 연 방은 null 일 수 있다 */
    @Column(name = "instance_no")
    private Integer instanceNo;

    public static TowerInstance open(int floorNumber, int instanceNo, LocalDateTime now) {
        return TowerInstance.builder()
                .floorNumber(floorNumber)
                .instanceNo(instanceNo)
                .openedAt(now)
                .build();
    }

    /** LUT-738: 번호 없는 방(롤링 배포 중 옛 코드가 연 방)에 번호를 채운다 */
    public void assignNumber(int instanceNo) {
        this.instanceNo = instanceNo;
    }

    /**
     * LUT-738: 화면에 보일 방 번호 — 번호가 없는 방은 쓰이지 않은 가장 작은 번호부터 id 순으로 대신 매긴다(저장하지 않는다).
     *
     * @param instances 같은 층의 방, id 순
     */
    public static java.util.Map<Long, Integer> displayNumbers(
            java.util.List<TowerInstance> instances) {
        java.util.Set<Integer> used = new java.util.HashSet<>();
        for (TowerInstance instance : instances) {
            if (instance.instanceNo != null) {
                used.add(instance.instanceNo);
            }
        }
        java.util.Map<Long, Integer> result = new java.util.LinkedHashMap<>();
        int next = 1;
        for (TowerInstance instance : instances) {
            if (instance.instanceNo != null) {
                result.put(instance.id, instance.instanceNo);
                continue;
            }
            while (used.contains(next)) {
                next++;
            }
            used.add(next);
            result.put(instance.id, next);
        }
        return result;
    }

    /** LUT-738: 이 층에서 쓰이지 않은 가장 작은 번호 */
    public static int nextNumber(java.util.Collection<Integer> used) {
        int next = 1;
        while (used.contains(next)) {
            next++;
        }
        return next;
    }
}
