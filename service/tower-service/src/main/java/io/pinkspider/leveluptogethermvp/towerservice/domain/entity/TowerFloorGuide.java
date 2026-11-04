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
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;

/**
 * LUT-734: 층별 첫 방문 안내 — 층당 1행. 안내 NPC 가 문장을 차례로 말하고 마지막 문장에 미션 시작 버튼(선택)을 둔다. 문장은 NPC 대사와 같은 4
 * locale·2줄 규칙. "다시 보여 주기" 로 저장할 때만 {@code version} 을 올린다.
 */
@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(
        name = "tower_floor_guide",
        uniqueConstraints =
                @UniqueConstraint(name = "uk_tower_floor_guide_floor", columnNames = "floor_id"))
@Comment("레벨업 탑 층별 첫 방문 안내")
public class TowerFloorGuide extends LocalDateTimeBaseEntity {

    public static final int MAX_LINES = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "floor_id", nullable = false)
    private Long floorId;

    @Column(name = "enabled", nullable = false)
    @Builder.Default
    private Boolean enabled = false;

    /** 안내 NPC (그 층 NPC). NPC 를 지우면 DB 가 NULL 로 — 안내가 뜨지 않는다 */
    @Column(name = "npc_id")
    private Long npcId;

    @Column(name = "show_mission_button", nullable = false)
    @Builder.Default
    private Boolean showMissionButton = false;

    @Column(name = "mission_button_label", length = 30)
    private String missionButtonLabel;

    @Column(name = "mission_button_label_en", length = 30)
    private String missionButtonLabelEn;

    @Column(name = "mission_button_label_ja", length = 30)
    private String missionButtonLabelJa;

    @Column(name = "mission_button_label_ar", length = 30)
    private String missionButtonLabelAr;

    @Column(name = "version", nullable = false)
    @Builder.Default
    private Integer version = 1;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "tower_floor_guide_line", joinColumns = @JoinColumn(name = "guide_id"))
    @OrderColumn(name = "line_order")
    @Builder.Default
    private List<TowerNpcLine> lines = new ArrayList<>();

    public boolean isEnabledGuide() {
        return Boolean.TRUE.equals(enabled);
    }

    public boolean isMissionButtonShown() {
        return Boolean.TRUE.equals(showMissionButton);
    }

    /** 내용을 바꾼다. {@code reshow} 면 버전을 올려 이미 본 유저에게도 한 번 더 보인다 */
    public void update(TowerFloorGuide source, boolean reshow) {
        this.enabled = source.enabled;
        this.npcId = source.npcId;
        this.showMissionButton = source.showMissionButton;
        this.missionButtonLabel = source.missionButtonLabel;
        this.missionButtonLabelEn = source.missionButtonLabelEn;
        this.missionButtonLabelJa = source.missionButtonLabelJa;
        this.missionButtonLabelAr = source.missionButtonLabelAr;
        this.lines.clear();
        this.lines.addAll(source.lines);
        if (reshow) {
            this.version = this.version + 1;
        }
    }
}
