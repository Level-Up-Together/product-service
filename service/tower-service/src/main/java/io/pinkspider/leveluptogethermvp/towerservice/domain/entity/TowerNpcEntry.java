package io.pinkspider.leveluptogethermvp.towerservice.domain.entity;

import io.pinkspider.global.converter.CryptoConverter;
import io.pinkspider.global.domain.auditentity.LocalDateTimeBaseEntity;
import io.pinkspider.leveluptogethermvp.towerservice.domain.enums.TowerNpcEntryStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;

/**
 * LUT-760: NPC 에 남긴 글 하나. 로그인 유저({@code userId}+닉네임 스냅샷) · 방문객({@code visitorKey}+이름 번호) · 운영
 * 시드({@code isSeed}, 작성자 표시는 NPC 이름) 중 하나다. 본문은 욕설 가림을 거친 값만 저장하고, 이메일은 암호화해 두었다가 이벤트가 끝나면 어드민이 일괄
 * 파기한다. 이메일은 공개 조회에 절대 나가지 않는다.
 */
@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(
        name = "tower_npc_entry",
        indexes = {
            @Index(name = "idx_tower_npc_entry_pick", columnList = "npc_id, status, view_count"),
            @Index(
                    name = "idx_tower_npc_entry_user_day",
                    columnList = "npc_id, user_id, created_at"),
            @Index(
                    name = "idx_tower_npc_entry_visitor_day",
                    columnList = "npc_id, visitor_key, created_at"),
            @Index(
                    name = "idx_tower_npc_entry_ip_day",
                    columnList = "npc_id, client_ip, created_at")
        })
@Comment("레벨업 탑 NPC 에 남긴 글")
public class TowerNpcEntry extends LocalDateTimeBaseEntity {

    /** 신고가 이 수에 이르면 자동으로 숨긴다 (어드민이 다시 공개할 수 있다) */
    public static final int AUTO_HIDE_REPORTS = 3;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "npc_id", nullable = false)
    private Long npcId;

    @Column(name = "user_id")
    private String userId;

    @Column(name = "visitor_key", length = 64)
    private String visitorKey;

    @Column(name = "visitor_no")
    private Integer visitorNo;

    @Column(name = "client_ip", length = 64)
    private String clientIp;

    @Column(name = "author_name", length = 50)
    private String authorName;

    @Column(name = "body", nullable = false, length = 500)
    private String body;

    @Convert(converter = CryptoConverter.class)
    @Column(name = "email", length = 500)
    private String email;

    @Column(name = "consent_at")
    private LocalDateTime consentAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    @Builder.Default
    private TowerNpcEntryStatus status = TowerNpcEntryStatus.VISIBLE;

    @Column(name = "is_seed", nullable = false)
    @Builder.Default
    private Boolean isSeed = false;

    @Column(name = "report_count", nullable = false)
    @Builder.Default
    private Integer reportCount = 0;

    @Column(name = "view_count", nullable = false)
    @Builder.Default
    private Integer viewCount = 0;

    public boolean isSeedEntry() {
        return Boolean.TRUE.equals(isSeed);
    }

    public boolean isVisible() {
        return status == TowerNpcEntryStatus.VISIBLE;
    }

    /** 신고 1건 반영 — 임계에 이르면 숨긴다. 돌려주는 값은 이번 신고로 숨겨졌는가 */
    public boolean addReport() {
        this.reportCount = (reportCount == null ? 0 : reportCount) + 1;
        if (isVisible() && this.reportCount >= AUTO_HIDE_REPORTS) {
            this.status = TowerNpcEntryStatus.HIDDEN;
            return true;
        }
        return false;
    }

    public void changeStatus(TowerNpcEntryStatus status) {
        this.status = status;
    }

    /** 이메일 파기 — 본문은 남긴다 */
    public void purgeEmail() {
        this.email = null;
    }
}
