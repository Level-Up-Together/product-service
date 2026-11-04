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
 * LUT-633: 탑 채팅 운영 로그 — 원문은 다른 유저에게 절대 나가지 않고 여기(신고 판단용)에만 남는다. 거절된 메시지도 남긴다. 보관 기간·어드민 화면은 미결(기획
 * 확정 후).
 */
@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(name = "tower_chat_log")
@Comment("레벨업 탑 채팅 로그 (원문 포함, 운영용)")
public class TowerChatLog extends LocalDateTimeBaseEntity {
    public static final String RESULT_SENT = "SENT";
    public static final String RESULT_MASKED = "MASKED";
    public static final String RESULT_REJECTED = "REJECTED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "instance_id", nullable = false)
    private Long instanceId;

    @Column(name = "floor_number", nullable = false)
    private Integer floorNumber;

    @Column(name = "occupant_id")
    private Long occupantId;

    @Column(name = "user_id")
    private String userId;

    @Column(name = "visitor_key", length = 64)
    private String visitorKey;

    @Column(name = "original_content", nullable = false, length = 1000)
    @Comment("보낸 원문 (운영 로그 전용)")
    private String originalContent;

    @Column(name = "processed_content", length = 1000)
    @Comment("가린 뒤 실제로 나간 문장 (거절이면 NULL)")
    private String processedContent;

    @Column(name = "result", nullable = false, length = 20)
    @Comment("SENT / MASKED / REJECTED")
    private String result;

    @Column(name = "reject_code", length = 10)
    private String rejectCode;

    @Column(name = "matched_words", length = 500)
    @Comment("걸린 금칙어 (쉼표)")
    private String matchedWords;
}
