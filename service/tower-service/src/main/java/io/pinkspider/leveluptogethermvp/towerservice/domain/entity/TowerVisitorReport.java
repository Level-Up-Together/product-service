package io.pinkspider.leveluptogethermvp.towerservice.domain.entity;

import io.pinkspider.global.domain.auditentity.LocalDateTimeBaseEntity;
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
import org.hibernate.annotations.Comment;

/**
 * LUT-705: 방문객 신고 — 계정이 없으므로 신고 시점의 방문객 세션(키·번호)·기기 정보(IP·기기 ID·User-Agent)와 최근 원문 채팅을 함께 남긴다. 운영자는
 * 이 정보로 기기·IP 제재를 건다. 원문은 운영 전용이다.
 */
@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(name = "tower_visitor_report")
@Comment("레벨업 탑 방문객 신고 (원문·기기 정보 포함, 운영 전용)")
public class TowerVisitorReport extends LocalDateTimeBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "reporter_user_id", nullable = false)
    private String reporterUserId;

    @Column(name = "instance_id", nullable = false)
    private Long instanceId;

    @Column(name = "floor_number", nullable = false)
    private Integer floorNumber;

    @Column(name = "target_occupant_id", nullable = false)
    private Long targetOccupantId;

    @Column(name = "visitor_key", nullable = false, length = 64)
    private String visitorKey;

    @Column(name = "visitor_no")
    private Integer visitorNo;

    @Column(name = "client_ip", length = 64)
    private String clientIp;

    @Column(name = "device_id", length = 128)
    private String deviceId;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "recent_messages", columnDefinition = "TEXT")
    private String recentMessages;
}
