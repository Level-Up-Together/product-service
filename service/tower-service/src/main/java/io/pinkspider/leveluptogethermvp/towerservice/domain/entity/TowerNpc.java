package io.pinkspider.leveluptogethermvp.towerservice.domain.entity;

import io.pinkspider.global.domain.auditentity.LocalDateTimeBaseEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;

/**
 * LUT-693: 탑 NPC(층 주민). 층마다 어드민이 등록하고(층당 최대 3명, 다른 층에 같은 캐릭터를 쓰려면 그 층에 다시 등록), 걸어 다니지 않고 등록한 자리에
 * 머문다. 점유자({@link TowerOccupant})가 아니므로 인원 표시·인스턴스 배정·지표에 들어가지 않는다. 비공개 NPC 는 탑에 나타나지 않는다(검수용).
 */
@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(name = "tower_npc", indexes = @Index(name = "idx_tower_npc_floor", columnList = "floor_id"))
@Comment("레벨업 탑 NPC")
public class TowerNpc extends LocalDateTimeBaseEntity {

    public static final int MAX_PER_FLOOR = 3;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "floor_id", nullable = false)
    private Long floorId;

    @Column(name = "name", nullable = false, length = 30)
    private String name;

    @Column(name = "name_en", nullable = false, length = 30)
    private String nameEn;

    @Column(name = "name_ja", nullable = false, length = 30)
    private String nameJa;

    @Column(name = "name_ar", nullable = false, length = 30)
    private String nameAr;

    @Column(name = "profile_image_url", nullable = false, length = 500)
    private String profileImageUrl;

    @Column(name = "status_text", length = 30)
    private String statusText;

    @Column(name = "status_text_en", length = 30)
    private String statusTextEn;

    @Column(name = "status_text_ja", length = 30)
    private String statusTextJa;

    @Column(name = "status_text_ar", length = 30)
    private String statusTextAr;

    @Column(name = "seat_x", nullable = false)
    private Integer seatX;

    @Column(name = "seat_y", nullable = false)
    private Integer seatY;

    @Column(name = "is_public", nullable = false)
    @Builder.Default
    private Boolean isPublic = false;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private Integer sortOrder = 0;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "tower_npc_line", joinColumns = @JoinColumn(name = "npc_id"))
    @OrderColumn(name = "line_order")
    @Builder.Default
    private List<TowerNpcLine> lines = new ArrayList<>();

    public boolean isPublicNpc() {
        return Boolean.TRUE.equals(isPublic);
    }

    /** 수정 — 층은 바꾸지 않는다(다른 층에 쓰려면 그 층에 다시 등록) */
    public void update(TowerNpc source) {
        this.name = source.name;
        this.nameEn = source.nameEn;
        this.nameJa = source.nameJa;
        this.nameAr = source.nameAr;
        this.profileImageUrl = source.profileImageUrl;
        this.statusText = source.statusText;
        this.statusTextEn = source.statusTextEn;
        this.statusTextJa = source.statusTextJa;
        this.statusTextAr = source.statusTextAr;
        this.seatX = source.seatX;
        this.seatY = source.seatY;
        this.isPublic = source.isPublic;
        this.sortOrder = source.sortOrder;
        this.lines.clear();
        this.lines.addAll(source.lines);
    }
}
